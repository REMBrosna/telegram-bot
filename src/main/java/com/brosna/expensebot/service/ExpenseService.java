package com.brosna.expensebot.service;

import com.brosna.expensebot.config.AppProperties;
import com.brosna.expensebot.domain.Budget;
import com.brosna.expensebot.domain.Expense;
import com.brosna.expensebot.model.ParsedExpense;
import com.brosna.expensebot.repository.BudgetRepository;
import com.brosna.expensebot.repository.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;
    private final ExpenseParser expenseParser;
    private final CategoryDetector categoryDetector;
    private final AppProperties appProperties;

    public ExpenseService(
            ExpenseRepository expenseRepository,
            BudgetRepository budgetRepository,
            ExpenseParser expenseParser,
            CategoryDetector categoryDetector,
            AppProperties appProperties
    ) {
        this.expenseRepository = expenseRepository;
        this.budgetRepository = budgetRepository;
        this.expenseParser = expenseParser;
        this.categoryDetector = categoryDetector;
        this.appProperties = appProperties;
    }

    public String addExpense(Long userId, String command) {
        ParsedExpense parsed = expenseParser.parse(command);

        String category =
                categoryDetector.detect(parsed.description());

        Expense expense = expenseRepository.save(
                new Expense(
                        userId,
                        parsed.amount(),
                        parsed.currency(),
                        category,
                        parsed.description(),
                        Instant.now()
                )
        );

        return """
                ✅ Expense added
                #%d
                %s %s
                %s
                %s
                """.formatted(
                expense.getId(),
                emoji(category),
                category,
                expense.getDescription(),
                formatMoney(expense.getAmount(), expense.getCurrency())
        ).trim();
    }

    @Transactional(readOnly = true)
    public String todaySummary(Long userId) {
        ZoneId zoneId = appProperties.zoneId();
        LocalDate today = LocalDate.now(zoneId);

        Instant start = today.atStartOfDay(zoneId).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zoneId).toInstant();

        List<Expense> expenses =
                expenseRepository
                        .findByTelegramUserIdAndExpenseDateGreaterThanEqualAndExpenseDateLessThanOrderByExpenseDateDesc(
                                userId,
                                start,
                                end
                        );

        if (expenses.isEmpty()) {
            return "📭 No expenses today.";
        }

        StringBuilder result =
                new StringBuilder("📅 Today's Expenses\n\n");

        for (Expense expense : expenses) {
            result.append("#")
                    .append(expense.getId())
                    .append(" ")
                    .append(emoji(expense.getCategory()))
                    .append(" ")
                    .append(expense.getDescription())
                    .append(" — ")
                    .append(formatMoney(expense.getAmount(), expense.getCurrency()))
                    .append("\n");
        }

        appendCurrencyTotals(result, expenses);

        return result.toString().trim();
    }

    @Transactional(readOnly = true)
    public String monthSummary(Long userId) {
        ZoneId zoneId = appProperties.zoneId();
        LocalDate today = LocalDate.now(zoneId);
        LocalDate firstDay = today.withDayOfMonth(1);

        Instant start =
                firstDay.atStartOfDay(zoneId).toInstant();

        Instant end =
                firstDay.plusMonths(1).atStartOfDay(zoneId).toInstant();

        List<Expense> expenses =
                expenseRepository
                        .findByTelegramUserIdAndExpenseDateGreaterThanEqualAndExpenseDateLessThanOrderByExpenseDateDesc(
                                userId,
                                start,
                                end
                        );

        String month =
                today.getMonth()
                        .getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        if (expenses.isEmpty()) {
            return "📭 No expenses for " + month + " " + today.getYear() + ".";
        }

        StringBuilder result =
                new StringBuilder("📊 ")
                        .append(month)
                        .append(" ")
                        .append(today.getYear())
                        .append("\n");

        Map<String, List<Expense>> byCurrency =
                expenses.stream()
                        .collect(Collectors.groupingBy(
                                Expense::getCurrency,
                                TreeMap::new,
                                Collectors.toList()
                        ));

        for (Map.Entry<String, List<Expense>> currencyEntry : byCurrency.entrySet()) {
            String currency = currencyEntry.getKey();
            List<Expense> currencyExpenses = currencyEntry.getValue();

            result.append("\n")
                    .append(currency)
                    .append("\n");

            Map<String, BigDecimal> categoryTotals =
                    currencyExpenses.stream()
                            .collect(
                                    Collectors.groupingBy(
                                            Expense::getCategory,
                                            Collectors.reducing(
                                                    BigDecimal.ZERO,
                                                    Expense::getAmount,
                                                    BigDecimal::add
                                            )
                                    )
                            );

            categoryTotals.entrySet()
                    .stream()
                    .sorted(
                            Map.Entry.<String, BigDecimal>
                                    comparingByValue()
                                    .reversed()
                    )
                    .forEach(entry ->
                            result.append(emoji(entry.getKey()))
                                    .append(" ")
                                    .append(entry.getKey())
                                    .append(": ")
                                    .append(formatMoney(entry.getValue(), currency))
                                    .append("\n")
                    );

            BigDecimal total =
                    currencyExpenses.stream()
                            .map(Expense::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            result.append("\n💰 Total ")
                    .append(currency)
                    .append(": ")
                    .append(formatMoney(total, currency))
                    .append("\n");

            budgetRepository
                    .findByTelegramUserIdAndCurrency(userId, currency)
                    .ifPresent(budget ->
                            appendBudgetStatus(
                                    result,
                                    budget.getAmount(),
                                    total,
                                    currency
                            )
                    );
        }

        return result.toString().trim();
    }

    @Transactional(readOnly = true)
    public String history(Long userId) {
        List<Expense> expenses =
                expenseRepository
                        .findTop10ByTelegramUserIdOrderByExpenseDateDesc(userId);

        if (expenses.isEmpty()) {
            return "📭 No expenses yet.";
        }

        ZoneId zoneId = appProperties.zoneId();

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("dd MMM HH:mm");

        StringBuilder result =
                new StringBuilder("🧾 Last 10 Expenses\n\n");

        for (Expense expense : expenses) {
            String date =
                    expense.getExpenseDate()
                            .atZone(zoneId)
                            .format(formatter);

            result.append("#")
                    .append(expense.getId())
                    .append(" ")
                    .append(date)
                    .append(" ")
                    .append(emoji(expense.getCategory()))
                    .append(" ")
                    .append(expense.getDescription())
                    .append(" — ")
                    .append(formatMoney(
                            expense.getAmount(),
                            expense.getCurrency()
                    ))
                    .append("\n");
        }

        result.append("\nDelete one with: /delete ID");

        return result.toString().trim();
    }

    public String deleteExpense(Long userId, String command) {
        String idText =
                command.replaceFirst("(?i)^/delete", "")
                        .trim();

        if (idText.isBlank()) {
            throw new IllegalArgumentException(
                    "Usage: /delete 15"
            );
        }

        Long id;

        try {
            id = Long.valueOf(idText);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Expense ID must be a number."
            );
        }

        Expense expense =
                expenseRepository
                        .findByIdAndTelegramUserId(id, userId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Expense #" + id + " was not found."
                                )
                        );

        expenseRepository.delete(expense);

        return "🗑 Deleted expense #" + id + ": "
                + expense.getDescription()
                + " — "
                + formatMoney(
                        expense.getAmount(),
                        expense.getCurrency()
                );
    }

    public String setBudget(Long userId, String command) {
        String body =
                command.replaceFirst("(?i)^/budget", "")
                        .trim();

        if (body.isBlank()) {
            throw new IllegalArgumentException(
                    "Usage: /budget 500 or /budget 2000000 khr"
            );
        }

        String[] parts = body.split("\\s+");

        BigDecimal amount;

        try {
            amount = new BigDecimal(parts[0].replace(",", ""));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid budget amount.");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Budget must be greater than zero."
            );
        }

        String currency =
                parts.length >= 2
                        ? expenseParser.normalizeCurrency(parts[1])
                        : "USD";

        Budget budget =
                budgetRepository
                        .findByTelegramUserIdAndCurrency(userId, currency)
                        .orElseGet(
                                () -> new Budget(
                                        userId,
                                        currency,
                                        amount
                                )
                        );

        budget.updateAmount(amount);

        budgetRepository.save(budget);

        return "🎯 Monthly "
                + currency
                + " budget set to "
                + formatMoney(amount, currency);
    }

    private void appendCurrencyTotals(
            StringBuilder result,
            List<Expense> expenses
    ) {
        Map<String, BigDecimal> totals =
                expenses.stream()
                        .collect(
                                Collectors.groupingBy(
                                        Expense::getCurrency,
                                        TreeMap::new,
                                        Collectors.reducing(
                                                BigDecimal.ZERO,
                                                Expense::getAmount,
                                                BigDecimal::add
                                        )
                                )
                        );

        result.append("\n");

        totals.forEach(
                (currency, total) ->
                        result.append("\n💰 Total ")
                                .append(currency)
                                .append(": ")
                                .append(formatMoney(total, currency))
        );
    }

    private void appendBudgetStatus(
            StringBuilder result,
            BigDecimal budget,
            BigDecimal spent,
            String currency
    ) {
        BigDecimal remaining =
                budget.subtract(spent);

        result.append("🎯 Budget: ")
                .append(formatMoney(budget, currency))
                .append("\n");

        if (remaining.signum() >= 0) {
            result.append("✅ Remaining: ")
                    .append(formatMoney(remaining, currency))
                    .append("\n");
        } else {
            result.append("⚠️ Over budget: ")
                    .append(
                            formatMoney(
                                    remaining.abs(),
                                    currency
                            )
                    )
                    .append("\n");
        }

        if (budget.signum() > 0) {
            BigDecimal percent =
                    spent.multiply(BigDecimal.valueOf(100))
                            .divide(
                                    budget,
                                    0,
                                    RoundingMode.HALF_UP
                            );

            result.append("📈 Used: ")
                    .append(percent)
                    .append("%\n");
        }
    }

    private String formatMoney(
            BigDecimal amount,
            String currency
    ) {
        if ("KHR".equals(currency)) {
            return amount.setScale(0, RoundingMode.HALF_UP)
                    .toPlainString()
                    + " KHR";
        }

        return "$"
                + amount.setScale(
                                2,
                                RoundingMode.HALF_UP
                        )
                        .toPlainString();
    }

    private String emoji(String category) {
        return switch (category) {
            case "FOOD" -> "🍔";
            case "CAR" -> "🚗";
            case "TRANSPORT" -> "🛺";
            case "SHOPPING" -> "🛍";
            case "BILL" -> "🧾";
            case "ENTERTAINMENT" -> "🎬";
            case "HEALTH" -> "💊";
            default -> "💵";
        };
    }
}
