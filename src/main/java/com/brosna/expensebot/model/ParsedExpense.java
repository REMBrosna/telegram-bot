package com.brosna.expensebot.model;

import java.math.BigDecimal;

public record ParsedExpense(
        BigDecimal amount,
        String currency,
        String description
) {
}
