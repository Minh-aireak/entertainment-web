package com.MyProject.debezium_manager.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.MyProject.debezium_manager.service.DebeziumConnectorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class HealthControllerTest {
    @Mock
    private DebeziumConnectorService debeziumConnectorService;

    @InjectMocks
    private HealthController healthController;

    @Test
    void ready_whenConnectorsAreRegistered_returnsOk() {
        when(debeziumConnectorService.isReady()).thenReturn(true);

        ResponseEntity<String> response = healthController.ready();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("READY", response.getBody());
    }

    @Test
    void ready_whenConnectorsAreNotRegistered_returnsServiceUnavailable() {
        when(debeziumConnectorService.isReady()).thenReturn(false);

        ResponseEntity<String> response = healthController.ready();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("NOT_READY", response.getBody());
    }
}
