package com.brosna.expensebot.service;

import com.brosna.expensebot.config.TelegramProperties;
import com.brosna.expensebot.telegram.TelegramApiClient;
import com.brosna.expensebot.telegram.TelegramMessageTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class TelegramCommandService {

    private static final Logger log = LoggerFactory.getLogger(TelegramCommandService.class);

    private final ExpenseService expenseService;
    private final TelegramApiClient telegramApiClient;
    private final TelegramMessageTracker messageTracker;
    private final TelegramProperties properties;

    public TelegramCommandService(
            ExpenseService expenseService,
            TelegramApiClient telegramApiClient,
            TelegramMessageTracker messageTracker,
            TelegramProperties properties
    ) {
        this.expenseService = expenseService;
        this.telegramApiClient = telegramApiClient;
        this.messageTracker = messageTracker;
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

        int messageId = message.path("message_id").asInt();

        String text = textNode.asText("").trim();
        if (text.isBlank()) {
            return;
        }

        if (!isAllowed(userId)) {
            sendAndTrack(chatId, "🔒 This is a private expense bot.");
            return;
        }

        messageTracker.track(chatId, messageId);

        try {
            if (isClearCommand(text)) {
                clearChat(chatId);
                return;
            }

            String response = route(userId, text);
            sendAndTrack(chatId, response);

        } catch (IllegalArgumentException ex) {
            sendAndTrack(chatId, "❌ " + ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to process Telegram update", ex);
            sendAndTrack(chatId, "❌ Something went wrong. Please try again.");
        }
    }

    private boolean isClearCommand(String text) {
        return text.equalsIgnoreCase("/clear");
    }

    private void clearChat(long chatId) {
        List<Integer> messageIds = messageTracker.drain(chatId);
        telegramApiClient.deleteMessages(chatId, messageIds);
    }

    private void sendAndTrack(long chatId, String text) {
        int messageId = telegramApiClient.sendMessage(chatId, text);
        messageTracker.track(chatId, messageId);
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
                /clear
                /help
                """;
    }

    private boolean isAllowed(long userId) {
        String allowed = properties.getAllowedUserId();

        if (allowed == null || allowed.isBlank()) {
            return true;
        }

        return Arrays.stream(allowed.split(","))
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .anyMatch(id -> isAllowedId(id, userId));
    }

    private boolean isAllowedId(String allowedId, long userId) {
        try {
            return Long.parseLong(allowedId) == userId;
        } catch (NumberFormatException ex) {
            log.warn("ALLOWED_TELEGRAM_USER_ID contains an invalid value: {}", allowedId);
            return false;
        }
    }

    private String help() {
        return """
                💰 Expense Bot

                Add an expense:
                /add 1.80 coffee
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

                Clear recent chat messages:
                /clear

                My Telegram ID:
                /whoami

                Default currency: USD
                Supported: USD, KHR
                """;
    }
}
