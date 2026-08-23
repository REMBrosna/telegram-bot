package com.brosna.expensebot.service;

import com.brosna.expensebot.model.ParsedExpense;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExpenseParserTest {

    private final ExpenseParser parser = new ExpenseParser();

    @Test
    void shouldParseDefaultUsd() {
        ParsedExpense result = parser.parse("/add 5.50 coffee");

        assertEquals(new BigDecimal("5.50"), result.amount());
        assertEquals("USD", result.currency());
        assertEquals("coffee", result.description());
    }

    @Test
    void shouldParseKhr() {
        ParsedExpense result = parser.parse("/add 25000 khr lunch");

        assertEquals(new BigDecimal("25000"), result.amount());
        assertEquals("KHR", result.currency());
        assertEquals("lunch", result.description());
    }

    @Test
    void shouldRejectNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("/add -5 coffee"));
    }
}
