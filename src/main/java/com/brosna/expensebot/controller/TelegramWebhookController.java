package com.brosna.expensebot.controller;

import com.brosna.expensebot.config.TelegramProperties;
import com.brosna.expensebot.service.TelegramCommandService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.Map;

@RestController
public class TelegramWebhookController {

    private final TelegramProperties properties;
    private final TelegramCommandService commandService;

    public TelegramWebhookController(TelegramProperties properties, TelegramCommandService commandService) {
        this.properties = properties;
        this.commandService = commandService;
    }

    @PostMapping("/telegram/webhook")
    public ResponseEntity<Void> telegramWebhook(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretToken,
            @RequestBody JsonNode update
    ) {
        if (secretToken == null || !secretToken.equals(properties.getWebhookSecret())) {
            return ResponseEntity.status(403).build();
        }

        commandService.handleUpdate(update);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/")
    public Map<String, Object> home() {
        return Map.of("application", "telegram-expense-bot", "status", "UP");
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
