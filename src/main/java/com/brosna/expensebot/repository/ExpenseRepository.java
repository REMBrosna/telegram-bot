package com.brosna.expensebot.repository;

import com.brosna.expensebot.domain.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByTelegramUserIdAndExpenseDateGreaterThanEqualAndExpenseDateLessThanOrderByExpenseDateDesc(
            Long telegramUserId,
            Instant start,
            Instant end
    );

    List<Expense> findTop10ByTelegramUserIdOrderByExpenseDateDesc(Long telegramUserId);

    Optional<Expense> findByIdAndTelegramUserId(Long id, Long telegramUserId);
}
