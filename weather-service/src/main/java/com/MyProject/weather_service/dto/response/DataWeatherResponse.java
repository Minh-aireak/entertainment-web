package com.MyProject.weather_service.dto.response;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DataWeatherResponse {
    List<ListForecast> list;
    City city;

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class ListForecast {
        Instant dt;
        Main main;
        List<Weather> weather;
        Clouds clouds;
        Wind wind;
        double pop;
        Rain rain;
        Sys sys;

        @JsonProperty("dt_txt")
        String dtTxt;
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Main {
        double temp;

        @JsonProperty("feels_like")
        double feelsLike;
        double humidity;
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Weather {
        String main;
        String description;
        String icon;
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Clouds {
        double all;
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Wind {
        double speed;
        double gust;
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Rain {
        double rain;

        @JsonSetter("3h")
        public double getRain() {
            return rain;
        }

        @JsonGetter("rain")
        public void setRain(double rain) {
            this.rain = rain;
        }
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Sys {
        String pod;
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class City {
        String name;
        String country;
    }
}
