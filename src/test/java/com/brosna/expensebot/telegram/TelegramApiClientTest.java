package com.brosna.expensebot.telegram;

import com.brosna.expensebot.config.TelegramProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class TelegramApiClientTest {

    @Test
    void createsRestClientWithoutSpringManagedBuilder() {
        TelegramProperties properties = new TelegramProperties();
        properties.setBotToken("123456789:test-token");

        assertDoesNotThrow(() -> new TelegramApiClient(properties));
    }
}
