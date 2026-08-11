package com.MyProject.debezium_manager.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DebeziumStartupListenerTest {
    @Mock
    private DebeziumConnectorService debeziumConnectorService;

    @InjectMocks
    private DebeziumStartupListener listener;

    @Test
    void onApplicationReady_registersAllConnectors() {
        listener.onApplicationReady();

        verify(debeziumConnectorService).registerAllConnectorsWithRetry();
    }
}
