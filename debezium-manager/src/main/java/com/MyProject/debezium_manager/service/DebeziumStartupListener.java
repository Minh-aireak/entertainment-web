package com.MyProject.debezium_manager.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class DebeziumStartupListener {
    private final DebeziumConnectorService debeziumConnectorService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Application is ready. Checking Kafka Connect health...");
        debeziumConnectorService.registerAllConnectorsWithRetry();
    }
}
