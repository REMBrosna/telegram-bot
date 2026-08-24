package com.brosna.expensebot.service;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;

@Component
public class CategoryDetector {

    public String detect(String description) {
        String text = description.toLowerCase(Locale.ROOT);

        if (contains(text, "coffee", "cafe", "latte", "cappuccino", "espresso", "americano")) {
            return "COFFEE";
        }

        if (contains(text, "food", "lunch", "dinner", "breakfast", "restaurant", "kfc",
                "pizza", "burger", "rice", "noodle", "drink")) {
            return "FOOD";
        }

        if (contains(text, "fuel", "gas", "petrol", "car wash", "garage", "engine", "oil",
                "parking", "tire", "tyre", "car service")) {
            return "CAR";
        }

        if (contains(text, "grab", "tuktuk", "tuk tuk", "taxi", "bus", "transport", "passapp")) {
            return "TRANSPORT";
        }

        if (contains(text, "shopping", "shirt", "shoe", "clothes", "mall", "bag", "watch")) {
            return "SHOPPING";
        }

        if (contains(text, "internet", "electric", "electricity", "water", "phone", "rent", "bill")) {
            return "BILL";
        }

        if (contains(text, "netflix", "spotify", "movie", "cinema", "game")) {
            return "ENTERTAINMENT";
        }

        if (contains(text, "doctor", "hospital", "medicine", "pharmacy", "clinic")) {
            return "HEALTH";
        }

        return "OTHER";
    }

    private boolean contains(String text, String... keywords) {
        return Arrays.stream(keywords).anyMatch(text::contains);
    }
}
