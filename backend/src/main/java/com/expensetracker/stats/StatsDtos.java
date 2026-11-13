package com.expensetracker.stats;

import com.expensetracker.expense.ExpenseDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class StatsDtos {

    private StatsDtos() {
    }

    public record Summary(
            PeriodType period,
            LocalDate start,
            LocalDate end,
            boolean current,
            /** Expenses only, like every figure below that does not mention income. */
            BigDecimal total,
            long count,
            BigDecimal averagePerDay,
            BigDecimal averagePerExpense,
            BigDecimal projectedTotal,
            BigDecimal previousTotal,
            BigDecimal previousComparableTotal,
            BigDecimal changePercent,
            List<CategoryTotal> byCategory,
            List<Bucket> breakdown,
            List<ExpenseDto> topExpenses,
            BigDecimal incomeTotal,
            /** Income minus expenses */
            BigDecimal balance,
            /** Share of income that was not spent; null without income */
            BigDecimal savingsRatePercent) {
    }

    public record CategoryTotal(Long categoryId, String name, String color, BigDecimal total, long count,
                                BigDecimal sharePercent) {
    }

    /** {@code total} and {@code count} cover expenses, {@code income} the income in the same range. */
    public record Bucket(LocalDate start, LocalDate end, BigDecimal total, long count, BigDecimal income) {
    }

    public record Trend(PeriodType period, List<Bucket> buckets, BigDecimal average, BigDecimal max,
                        BigDecimal min) {
    }
}
