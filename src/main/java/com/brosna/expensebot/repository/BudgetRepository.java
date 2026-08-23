package com.brosna.expensebot.repository;

import com.brosna.expensebot.domain.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    Optional<Budget> findByTelegramUserIdAndCurrency(
            Long telegramUserId,
            String currency
    );

    List<Budget> findByTelegramUserId(Long telegramUserId);
}
