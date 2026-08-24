package com.brosna.expensebot.controller;

import com.brosna.expensebot.config.TelegramProperties;
import com.brosna.expensebot.service.TelegramCommandService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/telegram")
public class TelegramWebhookController {

    static final String SECRET_TOKEN_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final TelegramCommandService commandService;
    private final byte[] expectedSecret;

    public TelegramWebhookController(
            TelegramProperties properties,
            TelegramCommandService commandService
    ) {
        this.commandService = commandService;
        this.expectedSecret = utf8(properties.getWebhookSecret());
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> receiveUpdate(
            @RequestHeader(value = SECRET_TOKEN_HEADER, required = false) String secretToken,
            @RequestBody JsonNode update
    ) {
        if (!isValidSecret(secretToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        commandService.handleUpdate(update);
        return ResponseEntity.noContent().build();
    }

    private boolean isValidSecret(String providedSecret) {
        byte[] provided = utf8(providedSecret);

        return expectedSecret.length > 0
                && provided.length > 0
                && MessageDigest.isEqual(expectedSecret, provided);
    }

    private static byte[] utf8(String value) {
        if (value == null || value.isBlank()) {
            return new byte[0];
        }

        return value.getBytes(StandardCharsets.UTF_8);
    }
}
