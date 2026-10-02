package com.MyProject.notification.notification_service.configuration;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;

/** Forwards the calling user's JWT - profile-service authenticates every endpoint, internal ones
 *  included. Deliberately not a @Component: that would make it global to every Feign client here,
 *  and EmailClient talks to Brevo, a third party that must never receive a user's token. It's
 *  wired per client instead, via @FeignClient(configuration = ...). */
public class AuthenticationRequestInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate requestTemplate) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            String tokenValue = jwtAuthenticationToken.getToken().getTokenValue();
            if (StringUtils.hasText(tokenValue)) {
                requestTemplate.header("Authorization", "Bearer " + tokenValue);
            }
        }
    }
}
