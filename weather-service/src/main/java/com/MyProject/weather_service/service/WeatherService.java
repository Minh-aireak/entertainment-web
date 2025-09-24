package com.MyProject.weather_service.service;

import com.MyProject.weather_service.dto.request.DataWeatherRequest;
import com.MyProject.weather_service.dto.response.DataWeatherResponse;
import com.MyProject.weather_service.repository.httpclient.OpenWeatherMapClient;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WeatherService {
    OpenWeatherMapClient client;

    @Transactional(rollbackFor = Exception.class)
    public DataWeatherResponse getDataWeather(DataWeatherRequest request){
        return client.getDataWeather(request.getLat(), request.getLon());
    }
}
