package com.MyProject.weather_service.controller;

import com.MyProject.weather_service.dto.request.DataWeatherRequest;
import com.MyProject.weather_service.dto.response.ApiResponse;
import com.MyProject.weather_service.dto.response.DataWeatherResponse;
import com.MyProject.weather_service.service.WeatherService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WeatherController {
    WeatherService weatherService;

    @PostMapping("/get-data-weather")
    ApiResponse<DataWeatherResponse> getForecast(@RequestBody DataWeatherRequest request){
        return ApiResponse.<DataWeatherResponse>builder()
                .result(weatherService.getDataWeather(request))
                .build();
    }
}
