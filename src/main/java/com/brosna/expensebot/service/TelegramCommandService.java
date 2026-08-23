package com.brosna.expensebot.service;

import com.brosna.expensebot.config.TelegramProperties;
import com.brosna.expensebot.telegram.TelegramApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.Locale;

@Service
public class TelegramCommandService {

    private static final Logger log = LoggerFactory.getLogger(TelegramCommandService.class);

    private final ExpenseService expenseService;
    private final TelegramApiClient telegramApiClient;
    private final TelegramProperties properties;

    public TelegramCommandService(
            ExpenseService expenseService,
            TelegramApiClient telegramApiClient,
            TelegramProperties properties
    ) {
        this.expenseService = expenseService;
        this.telegramApiClient = telegramApiClient;
        this.properties = properties;
    }

    public void handleUpdate(JsonNode update) {
        JsonNode message = update.get("message");
        if (message == null) {
            return;
        }

        JsonNode textNode = message.get("text");
        if (textNode == null) {
            return;
        }

        long chatId = message.path("chat")
                .path("id")
                .asLong();

        long userId = message.path("from")
                .path("id")
                .asLong();

        String text = textNode.asText("").trim();
        if (text.isBlank()) {
            return;
        }

        if (!isAllowed(userId)) {
            telegramApiClient.sendMessage(chatId, "🔒 This is a private expense bot.");
            return;
        }

        try {
            String response = route(userId, text);
            telegramApiClient.sendMessage(chatId, response);

        } catch (IllegalArgumentException ex) {
            telegramApiClient.sendMessage(chatId, "❌ " + ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to process Telegram update", ex);
            telegramApiClient.sendMessage(chatId, "❌ Something went wrong. Please try again.");
        }
    }

    private String route(long userId, String text) {
        String lower = text.toLowerCase(Locale.ROOT);

        if (lower.equals("/start") || lower.equals("/help")) {
            return help();
        }

        if (lower.equals("/whoami")) {
            return "👤 Your Telegram user ID: " + userId;
        }

        if (lower.equals("/today")) {
            return expenseService.todaySummary(userId);
        }

        if (lower.equals("/month")) {
            return expenseService.monthSummary(userId);
        }

        if (lower.equals("/history")) {
            return expenseService.history(userId);
        }

        if (lower.startsWith("/delete")) {
            return expenseService.deleteExpense(userId, text);
        }

        if (lower.startsWith("/budget")) {
            return expenseService.setBudget(userId, text);
        }

        if (lower.startsWith("/add")) {
            return expenseService.addExpense(userId, text);
        }

        if (Character.isDigit(text.charAt(0))) {
            return expenseService.addExpense(userId, text);
        }

        return """
                ❓ I don't understand that command.

                Try:
                /add 5 coffee
                /add 20000 khr lunch
                /today
                /month
                /history
                /budget 500
                /delete 15
                /help
                """;
    }

    private boolean isAllowed(long userId) {
        String allowed = properties.getAllowedUserId();

        if (allowed == null || allowed.isBlank()) {
            return true;
        }

        try {
            return Long.parseLong(allowed.trim()) == userId;
        } catch (NumberFormatException ex) {
            log.warn("ALLOWED_TELEGRAM_USER_ID is not a valid number. Denying access for safety.");
            return false;
        }
    }

    private String help() {
        return """
                💰 Expense Bot

                Add an expense:
                /add 5 coffee
                /add 12.50 lunch
                /add 20000 khr breakfast

                You can also type:
                5 coffee
                25000 khr lunch

                Reports:
                /today
                /month
                /history

                Monthly budget:
                /budget 500
                /budget 2000000 khr

                Delete:
                /delete 15

                My Telegram ID:
                /whoami

                Default currency: USD
                Supported: USD, KHR
                """;
    }
}
