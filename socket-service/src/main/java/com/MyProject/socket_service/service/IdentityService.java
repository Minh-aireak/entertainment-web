package com.MyProject.socket_service.service;

import com.MyProject.socket_service.dto.request.IntrospectRequest;
import com.MyProject.socket_service.dto.response.IntrospectResponse;
import com.MyProject.socket_service.repository.httpclient.IdentityClient;
import feign.FeignException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class IdentityService {
    IdentityClient identityClient;

    public IntrospectResponse introspect(IntrospectRequest request){
        try {
            var result = identityClient.introspect(request);
            if (Objects.isNull(result)) {
                return IntrospectResponse.builder()
                        .valid(false)
                        .userId(null)
                        .build();
            }
            return result.getResult();
        } catch (FeignException exception) {
            return IntrospectResponse.builder()
                    .valid(false)
                    .userId(null)
                    .build();
        }
    }
}
