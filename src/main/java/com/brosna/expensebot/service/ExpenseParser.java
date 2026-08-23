package com.brosna.expensebot.service;

import com.brosna.expensebot.model.ParsedExpense;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;

@Component
public class ExpenseParser {

    public ParsedExpense parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(
                    "Usage: /add 5 coffee or /add 20000 khr lunch"
            );
        }

        String text = raw.trim();

        if (text.toLowerCase(Locale.ROOT).startsWith("/add")) {
            text = text.substring(4).trim();
        }

        String[] parts = text.split("\\s+");

        if (parts.length < 2) {
            throw new IllegalArgumentException(
                    "Usage: /add 5 coffee or /add 20000 khr lunch"
            );
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(parts[0].replace(",", ""));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid amount: " + parts[0]);
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }

        int descriptionStart = 1;
        String currency = "USD";

        if (parts.length >= 3 && isCurrency(parts[1])) {
            currency = normalizeCurrency(parts[1]);
            descriptionStart = 2;
        }

        if (descriptionStart >= parts.length) {
            throw new IllegalArgumentException("Please add a description, for example: /add 5 coffee");
        }

        StringBuilder description = new StringBuilder();

        for (int i = descriptionStart; i < parts.length; i++) {
            if (!description.isEmpty()) {
                description.append(" ");
            }
            description.append(parts[i]);
        }

        return new ParsedExpense(
                amount,
                currency,
                description.toString()
        );
    }

    public String normalizeCurrency(String value) {
        String currency = value.trim().toUpperCase(Locale.ROOT);

        return switch (currency) {
            case "$", "USD" -> "USD";
            case "KHR", "៛", "RIEL" -> "KHR";
            default -> throw new IllegalArgumentException(
                    "Supported currencies: USD or KHR."
            );
        };
    }

    private boolean isCurrency(String value) {
        try {
            normalizeCurrency(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
