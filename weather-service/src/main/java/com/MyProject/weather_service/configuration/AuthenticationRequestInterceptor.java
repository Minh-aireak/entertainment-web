package com.MyProject.weather_service.configuration;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;

@Component
public class AuthenticationRequestInterceptor implements RequestInterceptor {
    @Value("${open-weather-map.api-key}")
    String apiKey;

    @Value("${open-weather-map.lang}")
    String lang;

    @Value("${open-weather-map.units}")
    String units;

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

        requestTemplate.query("appid", apiKey);
        requestTemplate.query("lang", lang);
        requestTemplate.query("units", units);
    }
}
