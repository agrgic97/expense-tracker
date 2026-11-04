package com.expensetracker.stats;

import com.expensetracker.category.Category;
import com.expensetracker.expense.Expense;
import com.expensetracker.expense.ExpenseDto;
import com.expensetracker.expense.ExpenseRepository;
import com.expensetracker.stats.StatsDtos.Bucket;
import com.expensetracker.stats.StatsDtos.CategoryTotal;
import com.expensetracker.stats.StatsDtos.Summary;
import com.expensetracker.stats.StatsDtos.Trend;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StatsService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ExpenseRepository expenses;
    private final Clock clock;

    public StatsService(ExpenseRepository expenses, Clock clock) {
        this.expenses = expenses;
        this.clock = clock;
    }

    public Summary summary(PeriodType type, LocalDate date) {
        LocalDate today = LocalDate.now(clock);
        DateRange range = DateRange.of(type, date);
        boolean current = range.contains(today);
        List<Expense> items = expenses.findAllInRange(range.start(), range.end());

        BigDecimal total = sum(items);
        long count = items.size();

        // For the running period only the days up to today count; past and future periods use all days.
        long elapsedDays = current ? ChronoUnit.DAYS.between(range.start(), today) + 1 : range.days();
        BigDecimal averagePerDay = divide(total, BigDecimal.valueOf(elapsedDays));
        BigDecimal averagePerExpense = count == 0 ? BigDecimal.ZERO.setScale(2) : divide(total, BigDecimal.valueOf(count));
        BigDecimal projectedTotal = current
                ? averagePerDay.multiply(BigDecimal.valueOf(range.days())).setScale(2, RoundingMode.HALF_UP)
                : null;

        DateRange previous = DateRange.of(type, range.start().minusDays(1));
        BigDecimal previousTotal = expenses.sumInRange(previous.start(), previous.end());
        // Compare a running period against the same stretch of the previous one (e.g. 1.-4. Nov vs. 1.-4. Okt).
        BigDecimal previousComparableTotal = previousTotal;
        if (current) {
            LocalDate comparableEnd = today.minus(1, type.unit());
            if (comparableEnd.isAfter(previous.end())) {
                comparableEnd = previous.end();
            }
            previousComparableTotal = expenses.sumInRange(previous.start(), comparableEnd);
        }
        BigDecimal changePercent = previousComparableTotal.signum() == 0 ? null
                : total.subtract(previousComparableTotal)
                        .multiply(HUNDRED)
                        .divide(previousComparableTotal, 1, RoundingMode.HALF_UP);

        return new Summary(type, range.start(), range.end(), current, total, count, averagePerDay,
                averagePerExpense, projectedTotal, scaled(previousTotal), scaled(previousComparableTotal),
                changePercent, byCategory(items, total), breakdown(range, type, items), topExpenses(items));
    }

    public Trend trend(PeriodType type, LocalDate date, int count) {
        int periods = Math.clamp(count, 1, 60);
        DateRange last = DateRange.of(type, date);
        LocalDate firstStart = type.startOf(last.start().minus(periods - 1L, type.unit()));
        DateRange whole = new DateRange(firstStart, last.end());

        List<Bucket> buckets = bucketize(whole.split(type.unit()), expenses.findAllInRange(whole.start(), whole.end()));
        List<BigDecimal> totals = buckets.stream().map(Bucket::total).toList();
        BigDecimal average = divide(totals.stream().reduce(BigDecimal.ZERO, BigDecimal::add),
                BigDecimal.valueOf(totals.size()));
        return new Trend(type, buckets, average,
                totals.stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO),
                totals.stream().min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO));
    }

    private List<CategoryTotal> byCategory(List<Expense> items, BigDecimal total) {
        Map<Long, List<Expense>> grouped = new LinkedHashMap<>();
        for (Expense e : items) {
            grouped.computeIfAbsent(e.getCategory().getId(), id -> new ArrayList<>()).add(e);
        }
        return grouped.values().stream()
                .map(group -> {
                    Category c = group.getFirst().getCategory();
                    BigDecimal sum = sum(group);
                    BigDecimal share = total.signum() == 0 ? BigDecimal.ZERO
                            : sum.multiply(HUNDRED).divide(total, 1, RoundingMode.HALF_UP);
                    return new CategoryTotal(c.getId(), c.getName(), c.getColor(), sum, group.size(), share);
                })
                .sorted(Comparator.comparing(CategoryTotal::total).reversed())
                .toList();
    }

    private List<Bucket> breakdown(DateRange range, PeriodType type, List<Expense> items) {
        return bucketize(range.split(type.bucketUnit()), items);
    }

    private List<Bucket> bucketize(List<DateRange> ranges, List<Expense> items) {
        return ranges.stream()
                .map(r -> {
                    List<Expense> inRange = items.stream().filter(e -> r.contains(e.getDate())).toList();
                    return new Bucket(r.start(), r.end(), sum(inRange), inRange.size());
                })
                .toList();
    }

    private List<ExpenseDto> topExpenses(List<Expense> items) {
        return items.stream()
                .sorted(Comparator.comparing(Expense::getAmount).reversed())
                .limit(5)
                .map(ExpenseDto::of)
                .toList();
    }

    private static BigDecimal sum(List<Expense> items) {
        return scaled(items.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal divide(BigDecimal value, BigDecimal divisor) {
        return divisor.signum() == 0 ? BigDecimal.ZERO.setScale(2) : value.divide(divisor, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
