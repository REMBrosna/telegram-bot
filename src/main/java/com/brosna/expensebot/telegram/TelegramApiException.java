package com.brosna.expensebot.telegram;

public class TelegramApiException extends RuntimeException {

    public TelegramApiException(String message) {
        super(message);
    }

    public TelegramApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
