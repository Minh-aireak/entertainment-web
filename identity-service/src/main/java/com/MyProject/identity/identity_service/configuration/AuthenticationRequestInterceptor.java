package com.MyProject.identity.identity_service.configuration;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;

public class AuthenticationRequestInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate requestTemplate) {
        ServletRequestAttributes servletRequestAttributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (Objects.isNull(servletRequestAttributes))
            return;

        var authHeader = servletRequestAttributes.
                getRequest().getHeader("Authorization");

        if(StringUtils.hasText(authHeader))
            requestTemplate.header("Authorization", authHeader);
    }
}
