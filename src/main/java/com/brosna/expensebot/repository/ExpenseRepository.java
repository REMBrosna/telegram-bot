package com.brosna.expensebot.repository;

import com.brosna.expensebot.domain.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByTelegramUserIdAndExpenseDateGreaterThanEqualAndExpenseDateLessThanOrderByExpenseDateDesc(
            Long telegramUserId,
            Instant start,
            Instant end
    );

    @Query("""
            select e.currency as currency, sum(e.amount) as total
            from Expense e
            where e.telegramUserId = :telegramUserId
              and e.expenseDate >= :start
              and e.expenseDate < :end
            group by e.currency
            """)
    List<CurrencyTotal> sumByCurrencyForPeriod(
            @Param("telegramUserId") Long telegramUserId,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    @Query("""
            select e.currency as currency, e.category as category, sum(e.amount) as total
            from Expense e
            where e.telegramUserId = :telegramUserId
              and e.expenseDate >= :start
              and e.expenseDate < :end
            group by e.currency, e.category
            """)
    List<CategoryTotal> sumByCurrencyAndCategoryForPeriod(
            @Param("telegramUserId") Long telegramUserId,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    List<Expense> findTop10ByTelegramUserIdOrderByExpenseDateDesc(Long telegramUserId);

    Optional<Expense> findByIdAndTelegramUserId(Long id, Long telegramUserId);

    interface CurrencyTotal {
        String getCurrency();

        BigDecimal getTotal();
    }

    interface CategoryTotal {
        String getCurrency();

        String getCategory();

        BigDecimal getTotal();
    }
}
