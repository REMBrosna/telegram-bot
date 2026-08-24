package com.brosna.expensebot.service;

import com.brosna.expensebot.config.AppProperties;
import com.brosna.expensebot.domain.Budget;
import com.brosna.expensebot.domain.Expense;
import com.brosna.expensebot.model.ParsedExpense;
import com.brosna.expensebot.repository.BudgetRepository;
import com.brosna.expensebot.repository.ExpenseRepository.CurrencyTotal;
import com.brosna.expensebot.repository.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
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

        String category = categoryDetector.detect(parsed.description());

        Expense expense = new Expense(userId, parsed.amount(), parsed.currency(), category,
                parsed.description(), Instant.now());

        expense = expenseRepository.save(expense);

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
                                userId, start, end);

        if (expenses.isEmpty()) {
            StringBuilder result = new StringBuilder("📭 No expenses today.");
            appendMonthlyBudgetStatuses(result, userId, today, zoneId);
            return result.toString().trim();
        }

        StringBuilder result = new StringBuilder("📅 Today's Expenses\n\n");

        for (Expense expense : expenses) {
            result.append("#")
                    .append(expense.getId())
                    .append(" ")
                    .append(emoji(displayCategory(expense)))
                    .append(" ")
                    .append(expense.getDescription())
                    .append(" — ")
                    .append(formatMoney(expense.getAmount(), expense.getCurrency()))
                    .append("\n");
        }

        Map<String, BigDecimal> todayTotals = totalsByCurrency(expenses);
        BigDecimal grandTotalUsd = convertTotals(todayTotals, "USD");

        appendCurrencyTotals(result, todayTotals);
        appendGrandTotal(result, grandTotalUsd, todayTotals);
        appendMonthlyBudgetStatuses(result, userId, today, zoneId);

        return result.toString().trim();
    }

    @Transactional(readOnly = true)
    public String monthSummary(Long userId) {
        ZoneId zoneId = appProperties.zoneId();
        LocalDate today = LocalDate.now(zoneId);
        LocalDate firstDay = today.withDayOfMonth(1);

        Instant start = firstDay.atStartOfDay(zoneId).toInstant();
        Instant end = firstDay.plusMonths(1).atStartOfDay(zoneId).toInstant();

        String month = today.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        List<Expense> monthlyExpenses = expenseRepository
                .findByTelegramUserIdAndExpenseDateGreaterThanEqualAndExpenseDateLessThanOrderByExpenseDateDesc(
                        userId,
                        start,
                        end
                );
        Map<String, BigDecimal> currencyTotals = totalsByCurrency(monthlyExpenses);

        if (currencyTotals.isEmpty()) {
            return "📭 No expenses for " + month + " " + today.getYear() + ".";
        }

        Map<String, Budget> budgets = budgetRepository.findByTelegramUserId(userId)
                .stream()
                .collect(Collectors.toMap(Budget::getCurrency, budget -> budget));

        StringBuilder result = new StringBuilder("📊 ")
                .append(month)
                .append(" ")
                .append(today.getYear())
                .append("\n");

        Map<String, Map<String, BigDecimal>> categoryTotals = totalsByCurrencyAndCategory(monthlyExpenses);

        BigDecimal grandTotalUsd = BigDecimal.ZERO;

        for (Map.Entry<String, BigDecimal> currencyEntry : currencyTotals.entrySet()) {
            String currency = currencyEntry.getKey();
            BigDecimal total = currencyEntry.getValue();

            result.append("\n")
                    .append(currency)
                    .append("\n");

            categoryTotals.getOrDefault(currency, Map.of())
                    .entrySet()
                    .stream()
                    .sorted((first, second) -> second.getValue().compareTo(first.getValue()))
                    .forEach(entry ->
                            result.append(emoji(entry.getKey()))
                                    .append(" ")
                                    .append(entry.getKey())
                                    .append(": ")
                                    .append(formatMoney(entry.getValue(), currency))
                                    .append("\n")
                    );

            grandTotalUsd = grandTotalUsd.add(convertToUsd(total, currency));

            result.append("\n💰 Total ")
                    .append(currency)
                    .append(": ")
                    .append(formatMoney(total, currency))
                    .append("\n");

            appendBudgetStatusIfPresent(result, budgets, currencyTotals, currency);
        }

        appendGrandTotal(result, grandTotalUsd, currencyTotals);

        return result.toString().trim();
    }

    @Transactional(readOnly = true)
    public String history(Long userId) {
        List<Expense> expenses = expenseRepository.findTop10ByTelegramUserIdOrderByExpenseDateDesc(userId);

        if (expenses.isEmpty()) {
            return "📭 No expenses yet.";
        }

        ZoneId zoneId = appProperties.zoneId();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM HH:mm");

        StringBuilder result = new StringBuilder("🧾 Last 10 Expenses\n\n");

        for (Expense expense : expenses) {
            String date = expense.getExpenseDate().atZone(zoneId).format(formatter);

            result.append("#")
                    .append(expense.getId())
                    .append(" ")
                    .append(date)
                    .append(" ")
                    .append(emoji(displayCategory(expense)))
                    .append(" ")
                    .append(expense.getDescription())
                    .append(" — ")
                    .append(formatMoney(expense.getAmount(), expense.getCurrency()))
                    .append("\n");
        }

        result.append("\nDelete one with: /delete ID");

        return result.toString().trim();
    }

    public String deleteExpense(Long userId, String command) {
        String idText = command.replaceFirst("(?i)^/delete", "").trim();

        if (idText.isBlank()) {
            throw new IllegalArgumentException("Usage: /delete 15");
        }

        Long id;

        try {
            id = Long.valueOf(idText);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Expense ID must be a number.");
        }

        Expense expense = expenseRepository.findByIdAndTelegramUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Expense #" + id + " was not found."));

        expenseRepository.delete(expense);

        return "🗑 Deleted expense #" + id + ": "
                + expense.getDescription()
                + " — "
                + formatMoney(expense.getAmount(), expense.getCurrency());
    }

    public String setBudget(Long userId, String command) {
        String body = command.replaceFirst("(?i)^/budget", "").trim();

        if (body.isBlank()) {
            throw new IllegalArgumentException("Usage: /budget 500 or /budget 2000000 khr");
        }

        String[] parts = body.split("\\s+");

        BigDecimal amount;

        try {
            amount = new BigDecimal(parts[0].replace(",", ""));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid budget amount.");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Budget must be greater than zero.");
        }

        String currency = parts.length >= 2 ? expenseParser.normalizeCurrency(parts[1]) : "USD";

        Budget budget = budgetRepository.findByTelegramUserIdAndCurrency(userId, currency)
                .orElseGet(() -> new Budget(userId, currency, amount));

        budget.updateAmount(amount);

        budgetRepository.save(budget);
        return "🎯 Monthly " + currency + " budget set to " + formatMoney(amount, currency);
    }

    private void appendCurrencyTotals(StringBuilder result, Map<String, BigDecimal> totals) {
        result.append("\n");
        totals.forEach(
                (currency, total) ->
                        result.append("\n💰 Total ")
                                .append(currency)
                                .append(": ")
                                .append(formatMoney(total, currency))
        );
    }

    private void appendMonthlyBudgetStatuses(
            StringBuilder result,
            Long userId,
            LocalDate today,
            ZoneId zoneId
    ) {
        List<Budget> budgets = budgetRepository.findByTelegramUserId(userId);

        if (budgets.isEmpty()) {
            return;
        }

        LocalDate firstDay = today.withDayOfMonth(1);
        Instant start = firstDay.atStartOfDay(zoneId).toInstant();
        Instant end = firstDay.plusMonths(1).atStartOfDay(zoneId).toInstant();

        Map<String, BigDecimal> monthlyTotals = sumByCurrency(userId, start, end);

        result.append("\n\nMonthly budget");

        budgets.stream()
                .sorted((first, second) -> first.getCurrency().compareTo(second.getCurrency()))
                .forEach(budget -> {
                    String currency = budget.getCurrency();
                    BigDecimal spent = convertTotals(monthlyTotals, currency);

                    result.append("\n").append(currency).append("\n");
                    appendBudgetStatus(result, budget.getAmount(), spent, currency);
                });
    }

    private Map<String, BigDecimal> sumByCurrency(Long userId, Instant start, Instant end) {
        return expenseRepository.sumByCurrencyForPeriod(userId, start, end)
                .stream()
                .collect(Collectors.toMap(
                        CurrencyTotal::getCurrency,
                        CurrencyTotal::getTotal,
                        BigDecimal::add,
                        TreeMap::new
                ));
    }

    private Map<String, BigDecimal> totalsByCurrency(List<Expense> expenses) {
        return expenses.stream()
                .collect(Collectors.groupingBy(
                        Expense::getCurrency,
                        TreeMap::new,
                        Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)
                ));
    }

    private Map<String, Map<String, BigDecimal>> totalsByCurrencyAndCategory(List<Expense> expenses) {
        return expenses.stream()
                .collect(Collectors.groupingBy(
                        Expense::getCurrency,
                        TreeMap::new,
                        Collectors.groupingBy(
                                this::displayCategory,
                                TreeMap::new,
                                Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)
                        )
                ));
    }

    private String displayCategory(Expense expense) {
        String detectedCategory = categoryDetector.detect(expense.getDescription());

        if (!"OTHER".equals(detectedCategory)) {
            return detectedCategory;
        }

        return expense.getCategory();
    }

    private void appendBudgetStatusIfPresent(
            StringBuilder result,
            Map<String, Budget> budgets,
            Map<String, BigDecimal> currencyTotals,
            String currency
    ) {
        Budget budget = budgets.get(currency);

        if (budget != null) {
            appendBudgetStatus(result, budget.getAmount(), convertTotals(currencyTotals, currency), currency);
        }
    }

    private void appendBudgetStatus(StringBuilder result, BigDecimal budget, BigDecimal spent, String currency) {
        BigDecimal remaining = budget.subtract(spent);

        result.append("🎯 Budget: ")
                .append(formatMoney(budget, currency))
                .append("\n")
                .append("💸 Spent: ")
                .append(formatMoney(spent, currency))
                .append("\n");

        if (remaining.signum() >= 0) {
            result.append("✅ Remaining: ")
                    .append(formatMoney(remaining, currency))
                    .append("\n");
        } else {
            result.append("⚠️ Over budget: ")
                    .append(formatMoney(remaining.abs(), currency))
                    .append("\n");
        }

        if (budget.signum() > 0) {
            BigDecimal percent = spent.multiply(BigDecimal.valueOf(100))
                    .divide(budget, 0, RoundingMode.HALF_UP);

            result.append("📈 Used: ")
                    .append(percent)
                    .append("%\n");
        }
    }

    private BigDecimal convertTotals(Map<String, BigDecimal> totals, String targetCurrency) {
        return totals.entrySet()
                .stream()
                .map(entry -> convert(entry.getValue(), entry.getKey(), targetCurrency))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void appendGrandTotal(
            StringBuilder result,
            BigDecimal grandTotalUsd,
            Map<String, BigDecimal> currencyTotals
    ) {
        if (currencyTotals.size() <= 1) {
            return;
        }

        result.append("\n\n🌐 Grand total: ")
                .append(formatMoney(grandTotalUsd, "USD"))
                .append(" (1 USD = ")
                .append(formatRate(appProperties.khrToUsdRate()))
                .append(" KHR)");
    }

    private BigDecimal convertToUsd(BigDecimal amount, String currency) {
        return convert(amount, currency, "USD");
    }

    private BigDecimal convert(BigDecimal amount, String sourceCurrency, String targetCurrency) {
        if (sourceCurrency.equals(targetCurrency)) {
            return amount;
        }

        if ("KHR".equals(sourceCurrency) && "USD".equals(targetCurrency)) {
            return amount.divide(appProperties.khrToUsdRate(), 6, RoundingMode.HALF_UP);
        }

        if ("USD".equals(sourceCurrency) && "KHR".equals(targetCurrency)) {
            return amount.multiply(appProperties.khrToUsdRate());
        }

        throw new IllegalArgumentException(
                "Unsupported currency conversion: " + sourceCurrency + " to " + targetCurrency
        );
    }

    private String formatRate(BigDecimal rate) {
        return rate.stripTrailingZeros().toPlainString();
    }

    private String formatMoney(BigDecimal amount, String currency) {
        if ("KHR".equals(currency)) {
            NumberFormat formatter = NumberFormat.getIntegerInstance(Locale.US);
            formatter.setGroupingUsed(true);

            return formatter.format(amount.setScale(0, RoundingMode.HALF_UP)) + " KHR";
        }

        return "$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String emoji(String category) {
        return switch (category) {
            case "COFFEE" -> "\u2615";
            case "FOOD" -> "\uD83C\uDF7D\uFE0F";
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
