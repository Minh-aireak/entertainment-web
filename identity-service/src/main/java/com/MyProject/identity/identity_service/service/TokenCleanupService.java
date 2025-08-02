package com.MyProject.identity.identity_service.service;

import java.util.Date;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.MyProject.identity.identity_service.repository.InvalidatedTokenRepository;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TokenCleanupService {
    InvalidatedTokenRepository invalidatedTokenRepository;

    @Scheduled(fixedDelayString = "${jwt.cleanup.delay}", initialDelay = 5000)
    @Transactional
    public void cleanupExpiredTime() {
        int quantity = invalidatedTokenRepository.deleteAllByExpiryTimeBefore(new Date());
        log.info("Cleaned up {} expired tokens!", quantity);
    }
}
