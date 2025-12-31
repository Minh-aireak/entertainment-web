package com.MyProject.post.post_service.service;

import com.MyProject.post.post_service.exception.AppException;
import com.MyProject.post.post_service.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class DateTimeFormatter {
    Map<Long, Function<LocalDateTime, String>> strategyMap = new LinkedHashMap<>();

    public DateTimeFormatter() {
        strategyMap.put(60L, this::formatInSeconds);
        strategyMap.put(3600L, this::formatInMinutes);
        strategyMap.put(86400L, this::formatInHours);
        strategyMap.put(2592000L, this::formatInDays);
        strategyMap.put(31104000L, this::formatInMonths);
        strategyMap.put(Long.MAX_VALUE, this::formatInYears);
    }

    public String format(LocalDateTime localDateTime){
        long elapseSeconds = ChronoUnit.SECONDS.between(localDateTime, LocalDateTime.now());

        var strategy = strategyMap.entrySet()
                .stream()
                .filter(longFunctionEntry -> elapseSeconds < longFunctionEntry.getKey())
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.MAP_STRATEGY_EXCEPTION));

        return strategy.getValue().apply(localDateTime);
    }

    private String formatInSeconds(LocalDateTime localDateTime){
        long elapseSeconds = ChronoUnit.SECONDS.between(localDateTime, LocalDateTime.now());
        return elapseSeconds + " seconds";
    }

    private String formatInMinutes(LocalDateTime localDateTime){
        return ChronoUnit.MINUTES.between(localDateTime, LocalDateTime.now()) + " minutes ago";
    }

    private String formatInHours(LocalDateTime localDateTime){
        return ChronoUnit.HOURS.between(localDateTime, LocalDateTime.now()) + " hours ago";
    }

    private String formatInDays(LocalDateTime localDateTime){
        return ChronoUnit.DAYS.between(localDateTime, LocalDateTime.now()) + " days ago";
    }

    private String formatInMonths(LocalDateTime localDateTime){
        return ChronoUnit.MONTHS.between(localDateTime, LocalDateTime.now()) + " months ago";
    }

    private String formatInYears(LocalDateTime localDateTime){
        return ChronoUnit.YEARS.between(localDateTime, LocalDateTime.now()) + " years ago";
    }
}
