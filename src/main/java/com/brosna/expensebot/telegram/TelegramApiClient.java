package com.brosna.expensebot.telegram;

import com.brosna.expensebot.config.TelegramProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TelegramApiClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramApiClient.class);
    private static final String TELEGRAM_API_BASE_URL = "https://api.telegram.org/bot";
    private static final String WEBHOOK_PATH = "/telegram/webhook";

    private final TelegramProperties properties;
    private final RestClient restClient;

    public TelegramApiClient(
            TelegramProperties properties,
            RestClient.Builder restClientBuilder
    ) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(TELEGRAM_API_BASE_URL + requireText(properties.getBotToken(), "Telegram bot token"))
                .build();
    }

    public int sendMessage(long chatId, String text) {
        return sendMessage(chatId, text, null);
    }

    public int sendMessage(long chatId, String text, Map<String, Object> replyMarkup) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("chat_id", chatId);
        request.put("text", requireText(text, "Message text"));

        if (replyMarkup != null && !replyMarkup.isEmpty()) {
            request.put("reply_markup", replyMarkup);
        }

        JsonNode result = execute("sendMessage", request);
        return requireMessageId(result, "sendMessage");
    }

    public void editMessageText(long chatId, int messageId, String text) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("chat_id", chatId);
        request.put("message_id", messageId);
        request.put("text", requireText(text, "Message text"));

        execute("editMessageText", request);
    }

    public void answerCallbackQuery(String callbackQueryId, String text) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("callback_query_id", requireText(callbackQueryId, "Callback query ID"));

        if (text != null && !text.isBlank()) {
            request.put("text", text);
        }

        execute("answerCallbackQuery", request);
    }

    public void deleteMessages(long chatId, Collection<Integer> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }

        execute("deleteMessages", Map.of(
                "chat_id", chatId,
                "message_ids", messageIds
        ));
    }

    public void registerWebhook() {
        String webhookUrl = normalizeBaseUrl(properties.getAppBaseUrl()) + WEBHOOK_PATH;

        execute("setWebhook", Map.of(
                "url", webhookUrl,
                "secret_token", requireText(properties.getWebhookSecret(), "Telegram webhook secret"),
                "drop_pending_updates", false
        ));

        log.info("Telegram webhook registered at {}", webhookUrl);
    }

    private JsonNode execute(String method, Map<String, ?> request) {
        try {
            JsonNode response = restClient
                    .post()
                    .uri("/{method}", method)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                throw new TelegramApiException("Telegram API " + method + " returned an empty response.");
            }

            if (!response.path("ok").asBoolean(false)) {
                String description = response.path("description")
                        .asText("Telegram rejected the request.");
                throw new TelegramApiException("Telegram API " + method + " failed: " + description);
            }

            return response.path("result");
        } catch (RestClientResponseException ex) {
            throw new TelegramApiException(
                    "Telegram API " + method + " failed with HTTP " + ex.getStatusCode().value() + ".",
                    ex
            );
        }
    }

    private int requireMessageId(JsonNode result, String method) {
        int messageId = result.path("message_id").asInt();

        if (messageId <= 0) {
            throw new TelegramApiException(
                    "Telegram API " + method + " returned no valid message ID."
            );
        }

        return messageId;
    }

    private String normalizeBaseUrl(String value) {
        String baseUrl = requireText(value, "Application base URL");

        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        return baseUrl;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + " must be configured.");
        }

        return value.trim();
    }
}
