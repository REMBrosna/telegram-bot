package com.brosna.expensebot.controller;

import com.brosna.expensebot.config.TelegramProperties;
import com.brosna.expensebot.service.TelegramCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TelegramWebhookControllerTest {

    @Mock
    private TelegramCommandService commandService;

    private TelegramWebhookController controller;

    @BeforeEach
    void setUp() {
        TelegramProperties properties = new TelegramProperties();
        properties.setWebhookSecret("test-secret");
        controller = new TelegramWebhookController(properties, commandService);
    }

    @Test
    void acceptsUpdateWithValidSecret() {
        JsonNode update = mock(JsonNode.class);

        ResponseEntity<Void> response = controller.receiveUpdate("test-secret", update);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(commandService).handleUpdate(update);
    }

    @Test
    void rejectsUpdateWithInvalidSecret() {
        JsonNode update = mock(JsonNode.class);

        ResponseEntity<Void> response = controller.receiveUpdate("wrong-secret", update);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(commandService, never()).handleUpdate(update);
    }

    @Test
    void rejectsUpdateWhenSecretIsMissing() {
        JsonNode update = mock(JsonNode.class);

        ResponseEntity<Void> response = controller.receiveUpdate(null, update);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(commandService, never()).handleUpdate(update);
    }
}
