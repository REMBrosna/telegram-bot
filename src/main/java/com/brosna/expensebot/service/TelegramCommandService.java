package com.brosna.expensebot.service;

import com.brosna.expensebot.config.TelegramProperties;
import com.brosna.expensebot.model.ExpenseAddResult;
import com.brosna.expensebot.telegram.TelegramApiClient;
import com.brosna.expensebot.telegram.TelegramMessageTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class TelegramCommandService {

    private static final Logger log = LoggerFactory.getLogger(TelegramCommandService.class);
    private static final List<CategoryButton> CATEGORY_BUTTONS = List.of(
            new CategoryButton("COFFEE", "\u2615 Coffee"),
            new CategoryButton("FOOD", "\uD83C\uDF7D\uFE0F Food"),
            new CategoryButton("CAR", "\uD83D\uDE97 Car"),
            new CategoryButton("TRANSPORT", "\uD83D\uDEFA Transport"),
            new CategoryButton("SHOPPING", "\uD83D\uDECD\uFE0F Shopping"),
            new CategoryButton("BILL", "\uD83E\uDDFE Bill"),
            new CategoryButton("ENTERTAINMENT", "\uD83C\uDFAC Fun"),
            new CategoryButton("HEALTH", "\uD83D\uDC8A Health"),
            new CategoryButton("OTHER", "\uD83D\uDCB5 Other")
    );

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
        JsonNode callbackQuery = update.get("callback_query");
        if (callbackQuery != null) {
            handleCallbackQuery(callbackQuery);
            return;
        }

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

            BotResponse response = route(userId, text);
            sendAndTrack(chatId, response);

        } catch (IllegalArgumentException ex) {
            sendAndTrack(chatId, "❌ " + ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to process Telegram update", ex);
            sendAndTrack(chatId, "❌ Something went wrong. Please try again.");
        }
    }

    private void handleCallbackQuery(JsonNode callbackQuery) {

        String callbackQueryId = callbackQuery.path("id").asString("");
        long userId = callbackQuery.path("from").path("id").asLong();

        try {
            if (!isAllowed(userId)) {
                telegramApiClient.answerCallbackQuery(callbackQueryId, "This is a private expense bot.");
                return;
            }

            CategorySelection selection = parseCategorySelection(callbackQuery.path("data").asString(""));
            String response = expenseService.updateCategory(userId, selection.expenseId(), selection.category());

            JsonNode message = callbackQuery.get("message");
            if (message != null) {
                long chatId = message.path("chat").path("id").asLong();
                int messageId = message.path("message_id").asInt();
                telegramApiClient.editMessageText(chatId, messageId, response);
                messageTracker.track(chatId, messageId);
            }

            telegramApiClient.answerCallbackQuery(callbackQueryId, "Category updated");
        } catch (IllegalArgumentException ex) {
            telegramApiClient.answerCallbackQuery(callbackQueryId, ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to process Telegram callback query", ex);
            telegramApiClient.answerCallbackQuery(callbackQueryId, "Something went wrong. Please try again.");
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

    private void sendAndTrack(long chatId, BotResponse response) {
        int messageId = telegramApiClient.sendMessage(chatId, response.text(), response.replyMarkup());
        messageTracker.track(chatId, messageId);
    }

    private BotResponse route(long userId, String text) {
        String lower = text.toLowerCase(Locale.ROOT);

        switch (lower) {
            case "/start", "/help" -> {
                return BotResponse.text(help());
            }
            case "/whoami" -> {
                return BotResponse.text("👤 Your Telegram user ID: " + userId);
            }
            case "/today" -> {
                return BotResponse.text(expenseService.todaySummary(userId));
            }
            case "/month" -> {
                return BotResponse.text(expenseService.monthSummary(userId));
            }
            case "/history" -> {
                return BotResponse.text(expenseService.history(userId));
            }
        }

        if (lower.startsWith("/delete")) {
            return BotResponse.text(expenseService.deleteExpense(userId, text));
        }

        if (lower.startsWith("/budget")) {
            return BotResponse.text(expenseService.setBudget(userId, text));
        }

        if (lower.startsWith("/add")) {
            return addExpenseResponse(userId, text);
        }

        if (Character.isDigit(text.charAt(0))) {
            return addExpenseResponse(userId, text);
        }

        return BotResponse.text("""
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
                """);
    }

    private BotResponse addExpenseResponse(long userId, String text) {
        ExpenseAddResult result = expenseService.addExpense(userId, text);
        return new BotResponse(result.message(), categoryKeyboard(result.expenseId()));
    }

    private CategorySelection parseCategorySelection(String data) {
        String[] parts = data.split(":", 3);

        if (parts.length != 3 || !"cat".equals(parts[0])) {
            throw new IllegalArgumentException("Unsupported button.");
        }

        try {
            return new CategorySelection(Long.parseLong(parts[1]), parts[2]);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Unsupported expense ID.");
        }
    }

    private Map<String, Object> categoryKeyboard(Long expenseId) {
        return Map.of(
                "inline_keyboard",
                List.of(
                        List.of(categoryButton(expenseId, CATEGORY_BUTTONS.get(0)), categoryButton(expenseId, CATEGORY_BUTTONS.get(1))),
                        List.of(categoryButton(expenseId, CATEGORY_BUTTONS.get(2)), categoryButton(expenseId, CATEGORY_BUTTONS.get(3))),
                        List.of(categoryButton(expenseId, CATEGORY_BUTTONS.get(4)), categoryButton(expenseId, CATEGORY_BUTTONS.get(5))),
                        List.of(categoryButton(expenseId, CATEGORY_BUTTONS.get(6)), categoryButton(expenseId, CATEGORY_BUTTONS.get(7))),
                        List.of(categoryButton(expenseId, CATEGORY_BUTTONS.get(8)))
                )
        );
    }

    private Map<String, String> categoryButton(Long expenseId, CategoryButton button) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("text", button.label());
        result.put("callback_data", "cat:" + expenseId + ":" + button.category());
        return result;
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

    private record BotResponse(String text, Map<String, Object> replyMarkup) {

        private static BotResponse text(String text) {
            return new BotResponse(text, null);
        }
    }

    private record CategoryButton(String category, String label) {
    }

    private record CategorySelection(Long expenseId, String category) {
    }
}
