package com.MyProject.comment_service.configuration;

import com.MyProject.common.exception.AppException;
import com.MyProject.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class DateTimeFormatter {
    Map<Long, Function<Instant, String>> strategyMap = new LinkedHashMap<>();

    public DateTimeFormatter() {
        strategyMap.put(60L, this::formatInSeconds);
        strategyMap.put(3600L, this::formatInMinutes);
        strategyMap.put(86400L, this::formatInHours);
        strategyMap.put(2592000L, this::formatInDays);
        strategyMap.put(31104000L, this::formatInMonths);
        strategyMap.put(Long.MAX_VALUE, this::formatInYears);
    }

    public String format(Instant instant){
        Instant now = Instant.now().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        long elapseSeconds = ChronoUnit.SECONDS.between(instant, now);

        var strategy = strategyMap.entrySet()
                .stream()
                .filter(longFunctionEntry -> elapseSeconds < longFunctionEntry.getKey())
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.MAP_STRATEGY_EXCEPTION));

        return strategy.getValue().apply(instant);
    }

    private String formatInSeconds(Instant instant){
        Instant now = Instant.now().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        long elapseSeconds = ChronoUnit.SECONDS.between(instant, now);
        return elapseSeconds + " seconds ago";
    }

    private String formatInMinutes(Instant instant){
        Instant now = Instant.now().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        return ChronoUnit.MINUTES.between(instant, now) + " minutes ago";
    }

    private String formatInHours(Instant instant){
        Instant now = Instant.now().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        return ChronoUnit.HOURS.between(instant, now) + " hours ago";
    }

    private String formatInDays(Instant instant){
        Instant now = Instant.now().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        return ChronoUnit.DAYS.between(instant, now) + " days ago";
    }

    private String formatInMonths(Instant instant){
        Instant now = Instant.now().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        return ChronoUnit.MONTHS.between(instant, now) + " months ago";
    }

    private String formatInYears(Instant instant){
        Instant now = Instant.now().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        return ChronoUnit.YEARS.between(instant, now) + " years ago";
    }
}
