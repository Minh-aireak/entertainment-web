package com.MyProject.profile.profile_service.configuration;

import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfiguration {
    @Bean
    public Encoder multipartFormEncode() {
        return new SpringFormEncoder();
    }
}
