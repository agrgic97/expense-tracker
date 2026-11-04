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
            List<ExpenseDto> topExpenses) {
    }

    public record CategoryTotal(Long categoryId, String name, String color, BigDecimal total, long count,
                                BigDecimal sharePercent) {
    }

    public record Bucket(LocalDate start, LocalDate end, BigDecimal total, long count) {
    }

    public record Trend(PeriodType period, List<Bucket> buckets, BigDecimal average, BigDecimal max,
                        BigDecimal min) {
    }
}
