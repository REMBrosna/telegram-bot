package com.brosna.expensebot.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class TelegramWebhookRegistrar {

    private static final Logger log =
            LoggerFactory.getLogger(TelegramWebhookRegistrar.class);

    private final TelegramApiClient telegramApiClient;

    public TelegramWebhookRegistrar(
            TelegramApiClient telegramApiClient
    ) {
        this.telegramApiClient = telegramApiClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerWebhook() {
        try {
            telegramApiClient.registerWebhook();
        } catch (Exception ex) {
            log.error(
                    "Unable to register Telegram webhook. Check TELEGRAM_BOT_TOKEN, "
                            + "TELEGRAM_WEBHOOK_SECRET and APP_BASE_URL.",
                    ex
            );
        }
    }
}
