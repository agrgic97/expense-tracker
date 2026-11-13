package com.expensetracker.budget;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class BudgetDtos {

    private BudgetDtos() {
    }

    /**
     * Budget situation of one month. {@code elapsedPercent} is how much of the month has passed, so the client
     * can tell whether spending runs ahead of plan (100 for past months, 0 for future ones).
     */
    public record BudgetOverview(
            LocalDate start,
            LocalDate end,
            BigDecimal elapsedPercent,
            BigDecimal totalBudget,
            BigDecimal totalSpent,
            List<BudgetStatus> categories) {
    }

    /** {@code budget}, {@code percent} and {@code remaining} are null for categories without a budget. */
    public record BudgetStatus(
            Long categoryId,
            String name,
            String color,
            BigDecimal budget,
            BigDecimal spent,
            BigDecimal percent,
            BigDecimal remaining) {
    }
}
