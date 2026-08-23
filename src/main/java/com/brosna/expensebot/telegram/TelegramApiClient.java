package com.brosna.expensebot.telegram;

import com.brosna.expensebot.config.TelegramProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TelegramApiClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramApiClient.class);

    private final TelegramProperties properties;
    private final RestClient restClient;

    public TelegramApiClient(TelegramProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.create();
    }

    public int sendMessage(long chatId, String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);

        JsonNode response = post("sendMessage", body);
        return response.path("result").path("message_id").asInt();
    }

    public void deleteMessages(long chatId, Collection<Integer> messageIds) {
        if (messageIds.isEmpty()) {
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("message_ids", messageIds);

        post("deleteMessages", body);
    }

    public void registerWebhook() {
        String baseUrl = removeTrailingSlash(properties.getAppBaseUrl());
        String webhookUrl = baseUrl + "/telegram/webhook";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url", webhookUrl);
        body.put("secret_token", properties.getWebhookSecret());
        body.put("drop_pending_updates", false);

        JsonNode response = post("setWebhook", body);

        log.info("Telegram webhook registration response: {}", response);
    }

    private JsonNode post(String method, Map<String, Object> body) {
        String url = "https://api.telegram.org/bot" + properties.getBotToken() + "/" + method;

        return restClient
                .post()
                .uri(url)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    private String removeTrailingSlash(String value) {
        if (value == null) {
            return "";
        }

        return value.endsWith("/")
                ? value.substring(0, value.length() - 1)
                : value;
    }
}
