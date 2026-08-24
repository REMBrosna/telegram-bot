package com.brosna.expensebot.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private static final String APPLICATION_NAME = "telegram-expense-bot";

    @GetMapping("/")
    public Map<String, String> home() {
        return Map.of(
                "application", APPLICATION_NAME,
                "status", "UP"
        );
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
