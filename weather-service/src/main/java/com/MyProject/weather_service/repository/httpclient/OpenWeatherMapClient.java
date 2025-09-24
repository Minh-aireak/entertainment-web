package com.MyProject.weather_service.repository.httpclient;

import com.MyProject.weather_service.configuration.AuthenticationRequestInterceptor;
import com.MyProject.weather_service.dto.response.DataWeatherResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "weather-client", url = "${app.service.url.weather-client}",
        configuration = { AuthenticationRequestInterceptor.class})
public interface OpenWeatherMapClient {
    @GetMapping(value = "/data/2.5/forecast")
    DataWeatherResponse getDataWeather(@RequestParam("lat") String lat,
                                       @RequestParam("lon") String lon);

}
