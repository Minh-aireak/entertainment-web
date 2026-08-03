package com.MyProject.notification.notification_service.service;

import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.common.redis.RedisService;
import com.MyProject.notification.notification_service.exception.AppException;
import com.MyProject.notification.notification_service.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationProfileService {
    RedisService redisService;
    NotificationProfileExternalService notificationProfileExternalService;

    @CircuitBreaker(name = "notificationProfileService")
    @Retry(name = "notificationProfileService")
    public Map<String, UserProfileResponse> getProfiles(List<String> userIds) {
        Map<String, UserProfileResponse> profilesMap = new HashMap<>();
        List<String> keys = userIds.stream().map(id -> "profile:user:" + id).toList();
        List<UserProfileResponse> cachedValues = Collections.nCopies(keys.size(), null);

        try {
            cachedValues = redisService.multiGet(keys, new TypeReference<UserProfileResponse>() {});
        } catch (Exception e) {
            log.error("Failed to multiGet profiles from cache for keys: {}", keys, e);
        }

        List<String> missingUserIds = new ArrayList<>();
        for (int i = 0; i < userIds.size(); i++) {
            UserProfileResponse profile = (i < cachedValues.size()) ? cachedValues.get(i) : null;
            if (Objects.isNull(profile)) {
                missingUserIds.add(userIds.get(i));
            } else if (profile.getUserId() != null) { // Add null check for profile.getUserId()
                profilesMap.put(profile.getUserId(), profile);
            } else {
                missingUserIds.add(userIds.get(i));
            }
        }

        if (!missingUserIds.isEmpty()) {
            try {
                Map<String, UserProfileResponse> fetchedProfiles =
                        notificationProfileExternalService.getBulkUserProfiles(new HashSet<>(missingUserIds));

                if (fetchedProfiles != null) { // Add null check for fetchedProfiles
                    fetchedProfiles.forEach((id, profile) -> {
                        if (profile != null) { // Add null check for individual profile
                            try {
                                redisService.setWithExpiration("profile:user:" + id, profile, 1, TimeUnit.HOURS);
                            } catch (Exception e) {
                                log.error("Failed to cache profile for userId: {}", id, e);
                            }
                        }
                    });

                    profilesMap.putAll(fetchedProfiles);
                }
            } catch (Exception e) {
                log.error("Failed to fetch user profiles for notifications: {}", missingUserIds, e);
                throw new AppException(ErrorCode.BULK_USER_PROFILE);
            }
        }

        return profilesMap;
    }

    @CircuitBreaker(name = "notificationProfileService")
    @Retry(name = "notificationProfileService")
    public UserProfileResponse getProfile(String userId) {
        UserProfileResponse profile = null;
        try {
            profile = redisService.get("profile:user:" + userId, new TypeReference<UserProfileResponse>() {});
        } catch (Exception e) {
            log.error("Failed to get profile from cache for userId: {}", userId, e);
        }

        if (Objects.isNull(profile)) {
            try {
                Map<String, UserProfileResponse> data =
                        notificationProfileExternalService.getBulkUserProfiles(Set.of(userId));

                if (data != null) {
                    profile = data.get(userId);
                    if (profile != null) { // Add null check before caching
                        try {
                            redisService.setWithExpiration("profile:user:" + userId, profile, 1, TimeUnit.HOURS);
                        } catch (Exception e) {
                            log.error("Failed to set cache for profile userId: {}", userId, e);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to fetch profile for userId: {}", userId, e);
            }
        }

        return profile;
    }
}
