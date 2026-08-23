package com.brosna.expensebot.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "expenses",
        indexes = {
                @Index(
                        name = "idx_expense_user_date",
                        columnList = "telegram_user_id,expense_date"
                )
        }
)
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(name = "expense_date", nullable = false)
    private Instant expenseDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Expense() {
    }

    public Expense(
            Long telegramUserId,
            BigDecimal amount,
            String currency,
            String category,
            String description,
            Instant expenseDate
    ) {
        this.telegramUserId = telegramUserId;
        this.amount = amount;
        this.currency = currency;
        this.category = category;
        this.description = description;
        this.expenseDate = expenseDate;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getTelegramUserId() {
        return telegramUserId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public Instant getExpenseDate() {
        return expenseDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
