package com.MyProject.file.file_service.configuration;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Configuration
public class B2Config {

    // Endpoint B2 dạng S3-compatible có pattern s3.<region>.backblazeb2.com
    private static final Pattern ENDPOINT_REGION_PATTERN =
            Pattern.compile("^s3\\.([a-zA-Z0-9-]+)\\.backblazeb2\\.com$");
    private static final String FALLBACK_REGION = "us-west-004";

    // SDK mặc định chỉ có 50 connection dùng chung cho MỌI request S3Client (putObject, uploadPart,
    // headObject, copyObject...). Khi upload nhiều part song song (xem FileService.uploadInSelfDrivenMultipart)
    // cộng với nhiều user upload đồng thời, pool 50 connection rất dễ bị cạn — request mới phải xếp hàng
    // chờ connectionAcquisitionTimeout (mặc định 10s) trước khi request thật sự bắt đầu chạy, cộng dồn
    // với thời gian upload rất dễ vượt timeout phía client (vd. axios 15s).
    @Value("${b2.http.max-connections:100}")
    private int maxConnections;

    @Value("${b2.key-id}")
    private String keyId;

    @Value("${b2.application-key}")
    private String applicationKey;

    @Value("${b2.endpoint}")
    private String endpoint;

    @Value("${b2.region:}")
    private String configuredRegion;

    // Fails app startup with a clear message instead of letting a blank property surface later
    // as an opaque URISyntaxException/SdkClientException from deep inside the AWS SDK.
    @PostConstruct
    private void validateConfig() {
        requireNonBlank(keyId, "b2.key-id", "B2_KEY_ID");
        requireNonBlank(applicationKey, "b2.application-key", "B2_APPLICATION_KEY");
        requireNonBlank(endpoint, "b2.endpoint", "B2_ENDPOINT");
    }

    private static void requireNonBlank(String value, String property, String envVar) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "B2 is not configured: '" + property + "' is blank. Set the " + envVar +
                            " environment variable (e.g. in .env.local or a secret manager — the " +
                            "tracked .env intentionally ships it empty) before starting file-service.");
        }
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(endpointUri())
                .region(resolveRegion())
                .credentialsProvider(credentialsProvider())
                .httpClientBuilder(ApacheHttpClient.builder()
                        .maxConnections(maxConnections)
                        // Thời gian thiết lập TCP connection tới B2, không phải thời gian upload.
                        .connectionTimeout(Duration.ofSeconds(5))
                        // Thời gian tối đa 1 request chờ để lấy được connection rảnh từ pool khi pool đầy.
                        .connectionAcquisitionTimeout(Duration.ofSeconds(10))
                        // Đủ lớn cho part upload lớn (vài chục-trăm MB) qua kết nối chậm; không giới hạn
                        // ở mức mặc định của SDK vốn nhắm tới các API call nhỏ, nhanh.
                        .socketTimeout(Duration.ofMinutes(2)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(endpointUri())
                .region(resolveRegion())
                .credentialsProvider(credentialsProvider())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    private StaticCredentialsProvider credentialsProvider() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(keyId, applicationKey));
    }

    private URI endpointUri() {
        // b2.endpoint is documented as a bare host (e.g. "s3.us-west-004.backblazeb2.com"); tolerate
        // a scheme already being present instead of blindly prefixing "https://" onto it, which would
        // turn an already-full URL like "https://s3...." into "https://https://s3....".
        String withScheme = (endpoint.startsWith("http://") || endpoint.startsWith("https://"))
                ? endpoint
                : "https://" + endpoint;
        try {
            URI uri = new URI(withScheme);
            if (uri.getHost() == null) {
                throw new IllegalStateException(
                        "b2.endpoint ('" + endpoint + "', from B2_ENDPOINT) is not a valid host. " +
                                "Expected e.g. 's3.us-west-004.backblazeb2.com', without a scheme.");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IllegalStateException(
                    "b2.endpoint ('" + endpoint + "', from B2_ENDPOINT) is not a valid URI.", e);
        }
    }

    // B2 không có khái niệm "region" tách rời endpoint như AWS; SDK v2 vẫn yêu cầu một Region
    // hợp lệ để build chữ ký SigV4, nên ta suy ra nó từ chính endpoint nếu b2.region bị bỏ trống.
    private Region resolveRegion() {
        if (configuredRegion != null && !configuredRegion.isBlank()) {
            return Region.of(configuredRegion);
        }
        Matcher matcher = ENDPOINT_REGION_PATTERN.matcher(endpoint == null ? "" : endpoint);
        if (matcher.matches()) {
            return Region.of(matcher.group(1));
        }
        log.warn("Could not derive B2 region from endpoint '{}', falling back to '{}'. " +
                "Set b2.region explicitly if this is wrong.", endpoint, FALLBACK_REGION);
        return Region.of(FALLBACK_REGION);
    }
}
