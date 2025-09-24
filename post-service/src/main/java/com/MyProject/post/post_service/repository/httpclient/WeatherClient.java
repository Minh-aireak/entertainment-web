package com.MyProject.post.post_service.repository.httpclient;

import com.MyProject.post.post_service.configuration.AuthenticationRequestInterceptor;
import com.MyProject.post.post_service.dto.request.DataWeatherRequest;
import com.MyProject.post.post_service.dto.response.ApiResponse;
import com.MyProject.post.post_service.dto.response.DataWeatherResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "post-weather-service", url = "${app.services.weather.url}",
        configuration = {AuthenticationRequestInterceptor.class})
public interface WeatherClient {
    @PostMapping(value = "/get-data-weather")
    ApiResponse<DataWeatherResponse> getDataWeather(@RequestBody DataWeatherRequest request);
}
