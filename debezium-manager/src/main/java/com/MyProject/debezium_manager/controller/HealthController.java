package com.MyProject.debezium_manager.controller;

import com.MyProject.debezium_manager.service.DebeziumConnectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HealthController {
    private final DebeziumConnectorService debeziumConnectorService;

    @GetMapping("/health/ready")
    public ResponseEntity<String> ready() {
        if (debeziumConnectorService.isReady()) {
            return ResponseEntity.ok("READY");
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("NOT_READY");
    }
}
