package com.MyProject.identity.identity_service.service;

import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import com.MyProject.common_dto.event.dto.request.EmailRequest;
import com.MyProject.identity.identity_service.dto.request.*;
import com.MyProject.identity.identity_service.entity.Role;
import com.MyProject.identity.identity_service.repository.RoleRepository;
import com.MyProject.identity.identity_service.repository.httpclient.OutboundIdentityClient;
import com.MyProject.identity.identity_service.repository.httpclient.OutboundUserClient;
import com.MyProject.identity.identity_service.repository.httpclient.UserProfileClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.MyProject.identity.identity_service.dto.response.AuthenticationResponse;
import com.MyProject.identity.identity_service.entity.InvalidatedToken;
import com.MyProject.identity.identity_service.entity.User;
import com.MyProject.identity.identity_service.exception.AppException;
import com.MyProject.identity.identity_service.exception.ErrorCode;
import com.MyProject.identity.identity_service.repository.InvalidatedTokenRepository;
import com.MyProject.identity.identity_service.repository.UserRepository;
import com.MyProject.common_dto.event.dto.response.IntrospectResponse;
import com.MyProject.common_dto.event.dto.request.IntrospectRequest;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationService {
    UserRepository userRepository;
    PasswordEncoder passwordEncoder;
    InvalidatedTokenRepository invalidatedTokenRepository;
    OutboundIdentityClient outboundIdentityClient;
    OutboundUserClient outboundUserClient;
    UserProfileClient client;
    RoleRepository roleRepository;
    KafkaTemplate<String, Object> kafkaTemplate;

    @NonFinal
    @Value("${jwt.signerKey}")
    String signerKey;

    @NonFinal
    @Value("${jwt.valid-duration}")
    long validDuration;

    @NonFinal
    @Value("${jwt.refreshable-duration}")
    long refreshableDuration;

    @NonFinal
    @Value("${jwt.client-id}")
    String clientId;

    @NonFinal
    @Value("${jwt.client-secret}")
    String clientSecret;

    @NonFinal
    @Value("${jwt.redirect-uri}")
    String redirectUri;

    @NonFinal
    @Value("${jwt.grant-type}")
    String authorizationCode;

    @Transactional(rollbackFor = Exception.class)
    public AuthenticationResponse authentication(AuthenticationRequest request) {
        var user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (!user.isActive()) throw new AppException(ErrorCode.USER_NOT_ACTIVE);

        boolean authenticated = passwordEncoder.matches(request.getPassword(), user.getPassword());

        if (!authenticated) throw new AppException(ErrorCode.PASSWORD_INCORRECT);

        String token = generateToken(user);

        return AuthenticationResponse.builder()
                .token(token)
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public void logout(LogoutRequest request) {
        String userId = getUserId();
        if (checkJidAndSignerKey(request.getToken(), userId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        SignedJWT signedToken = verifyToken(request.getToken(), true);

        try {
            String jid = signedToken.getJWTClaimsSet().getJWTID();
            Date expiryTime = signedToken.getJWTClaimsSet().getExpirationTime();
            InvalidatedToken invalidatedToken = InvalidatedToken.builder().id(jid).expiryTime(expiryTime).build();

            invalidatedTokenRepository.save(invalidatedToken);
        } catch (ParseException e) {
            throw new AppException(ErrorCode.PARSE_EXCEPTION);
        }
    }

    public IntrospectResponse introspectResponse(IntrospectRequest request) {
        boolean checkValid = true;
        var token = request.getToken();
        SignedJWT signedJWT = verifyToken(token, false);

        try {
            return IntrospectResponse.builder()
                    .valid(checkValid)
                    .userId(signedJWT.getJWTClaimsSet().getClaim("userId").toString())
                    .build();
        } catch (ParseException e) {
            throw new AppException(ErrorCode.PARSE_EXCEPTION);
        }
    }

    private String getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null)
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
        return jwt.getClaim("userId");
    }

    private boolean checkJidAndSignerKey(String token, String expectedUserId) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(signerKey.getBytes());

            if(!signedJWT.verify(verifier)){
                return true;
            }

            String actualJid = signedJWT.getJWTClaimsSet().getClaim("userId").toString();

            return !actualJid.equals(expectedUserId);
        } catch (ParseException | JOSEException e) {
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }
    }

    private SignedJWT verifyToken(String token, boolean isRefresh) {

        try {
            JWSVerifier verifier = new MACVerifier(signerKey.getBytes());
            SignedJWT signedJWT = SignedJWT.parse(token);
            Date expiryTime = (isRefresh)
                    ? new Date(signedJWT
                    .getJWTClaimsSet()
                    .getIssueTime()
                    .toInstant()
                    .plus(refreshableDuration, ChronoUnit.SECONDS).toEpochMilli())
                    : signedJWT.getJWTClaimsSet().getExpirationTime();

            boolean verified = signedJWT.verify(verifier);

            if (!(verified && expiryTime.after(new Date()))) throw new AppException(ErrorCode.TOKEN_INVALID);

            if (invalidatedTokenRepository.existsById(signedJWT.getJWTClaimsSet().getJWTID()))
                throw new AppException((ErrorCode.TOKEN_ALREADY_INVALIDATED));

            return signedJWT;
        } catch (JOSEException | ParseException e) {
            throw new AppException(ErrorCode.VERIFY_TOKEN_FAILED);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthenticationResponse refreshToken(RefreshRequest request) {
        String userId = getUserId();

        if (checkJidAndSignerKey(request.getToken(), userId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        SignedJWT signJWT = verifyToken(request.getToken(), true);

        try {
            String jid = signJWT.getJWTClaimsSet().getJWTID();
            var jet = signJWT.getJWTClaimsSet().getExpirationTime();

            InvalidatedToken invalidatedToken =
                    InvalidatedToken.builder().id(jid).expiryTime(jet).build();

            invalidatedTokenRepository.save(invalidatedToken);

            String username = signJWT.getJWTClaimsSet().getSubject();

            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

            var token = generateToken(user);

            return AuthenticationResponse.builder().token(token).build();
        } catch (ParseException e) {
            throw new AppException(ErrorCode.PARSE_EXCEPTION);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthenticationResponse outboundAuthenticate(String code) {
        var response = outboundIdentityClient.exchangeToken(ExchangeTokenRequest
                .builder()
                        .code(code)
                        .clientId(clientId)
                        .clientSecret(clientSecret)
                        .redirectUri(redirectUri)
                        .grantType(authorizationCode)
                .build());

        var userInfo = outboundUserClient.getInfo("json", response.getAccessToken());

        var user = userRepository.findByEmail(userInfo.getEmail()).orElseGet(() -> {
            Role role = roleRepository.findById("USER").orElseThrow(()
                    -> new AppException(ErrorCode.ROLE_NOT_EXISTED));

            User newUser = User.builder()
                    .id("EM_" + userInfo.getId())
                    .username(userInfo.getEmail())
                    .email(userInfo.getEmail())
                    .password(UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                    .roles(Set.of(role))
                    .active(true)
                    .build();

            UserProfileCreationRequest creationRequest = UserProfileCreationRequest.builder()
                    .userId("EM_" + userInfo.getId())
                    .username(userInfo.getEmail())
                    .email(userInfo.getEmail())
                    .fistName(userInfo.getFamilyName())
                    .lastName(userInfo.getGivenName())
                    .joinDate(LocalDateTime.now())
                    .build();

            client.createProfile(creationRequest);

            EmailRequest emailRequest = EmailRequest.builder()
                    .channel("EMAIL")
                    .recipient(userInfo.getEmail())
                    .subject("Welcome to travelplanner!")
                    .body("Hello,\n" +
                            "You have successfully registered as a member of TravelPlanner with the following credentials:\n" +
                            "Username: " + userInfo.getEmail() +
                            "\n" +
                            "Password: " + newUser.getPassword() +
                            ".Please remember to change your password as soon as possible for your account security.")
                    .build();
            newUser.setPassword(passwordEncoder.encode(newUser.getPassword()));
            userRepository.save(newUser);
            kafkaTemplate.send("send-email", emailRequest);

            return newUser;
        });

        var token = generateToken(user);

        return AuthenticationResponse.builder()
                .token(token)
                .build();
    }

    private String buildScope(User user) {
        StringJoiner stringJoiner = new StringJoiner(" ");

        if (!CollectionUtils.isEmpty(user.getRoles())) {
            user.getRoles().forEach(role -> {
                stringJoiner.add("ROLE_" + role.getName());
                if (!CollectionUtils.isEmpty(role.getPermissions())) {
                    role.getPermissions().forEach(permission -> stringJoiner.add(permission.getName()));
                }
            });
        }
        return stringJoiner.toString();
    }

    private String generateToken(User user) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject(user.getUsername())
                .issuer("aireak.com")
                .issueTime(new Date())
                .expirationTime(new Date(
                        Instant.now().plus(validDuration, ChronoUnit.SECONDS).toEpochMilli()))
                .jwtID(UUID.randomUUID().toString())
                .claim("scope", buildScope(user))
                .claim("userId", user.getId())
                .build();
        Payload payload = new Payload(jwtClaimsSet.toJSONObject());

        JWSObject jwsObject = new JWSObject(header, payload);

        try {
            jwsObject.sign(new MACSigner(signerKey.getBytes()));
        } catch (Exception e) {
            throw new AppException(ErrorCode.SIGNER_EXCEPTION);
        }

        return jwsObject.serialize();
    }
}
