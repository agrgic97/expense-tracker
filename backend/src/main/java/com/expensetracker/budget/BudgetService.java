package com.expensetracker.budget;

import com.expensetracker.auth.CurrentUser;
import com.expensetracker.budget.BudgetDtos.BudgetOverview;
import com.expensetracker.budget.BudgetDtos.BudgetStatus;
import com.expensetracker.category.Category;
import com.expensetracker.category.CategoryService;
import com.expensetracker.expense.ExpenseRepository;
import com.expensetracker.stats.DateRange;
import com.expensetracker.stats.PeriodType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BudgetService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ExpenseRepository expenses;
    private final CategoryService categories;
    private final CurrentUser currentUser;
    private final Clock clock;

    public BudgetService(ExpenseRepository expenses, CategoryService categories, CurrentUser currentUser, Clock clock) {
        this.expenses = expenses;
        this.categories = categories;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    public BudgetOverview overview(LocalDate date) {
        DateRange month = DateRange.of(PeriodType.MONTH, date);
        Map<Long, BigDecimal> spentByCategory = new HashMap<>();
        for (Object[] row : expenses.sumByCategoryInRange(currentUser.id(), month.start(), month.end())) {
            spentByCategory.put((Long) row[0], (BigDecimal) row[1]);
        }

        List<BudgetStatus> statuses = categories.findAllEntities().stream()
                .filter(c -> !c.isIncome())
                .map(c -> status(c, spentByCategory.getOrDefault(c.getId(), BigDecimal.ZERO)))
                // Budgeted categories first, the most used up on top; the rest by spending
                .sorted(Comparator.comparing((BudgetStatus s) -> s.budget() == null)
                        .thenComparing(s -> s.percent() != null ? s.percent() : BigDecimal.ZERO, Comparator.reverseOrder())
                        .thenComparing(BudgetStatus::spent, Comparator.reverseOrder()))
                .toList();

        BigDecimal totalBudget = statuses.stream()
                .map(BudgetStatus::budget)
                .filter(b -> b != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSpent = statuses.stream()
                .filter(s -> s.budget() != null)
                .map(BudgetStatus::spent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BudgetOverview(month.start(), month.end(), elapsedPercent(month), scaled(totalBudget),
                scaled(totalSpent), statuses);
    }

    private BigDecimal elapsedPercent(DateRange month) {
        LocalDate today = LocalDate.now(clock);
        if (today.isBefore(month.start())) {
            return BigDecimal.ZERO.setScale(1);
        }
        if (today.isAfter(month.end())) {
            return HUNDRED.setScale(1);
        }
        long elapsed = ChronoUnit.DAYS.between(month.start(), today) + 1;
        return BigDecimal.valueOf(elapsed * 100).divide(BigDecimal.valueOf(month.days()), 1, RoundingMode.HALF_UP);
    }

    private static BudgetStatus status(Category category, BigDecimal spent) {
        BigDecimal budget = category.getMonthlyBudget();
        if (budget == null) {
            return new BudgetStatus(category.getId(), category.getName(), category.getColor(), null, scaled(spent),
                    null, null);
        }
        BigDecimal percent = spent.multiply(HUNDRED).divide(budget, 1, RoundingMode.HALF_UP);
        return new BudgetStatus(category.getId(), category.getName(), category.getColor(), scaled(budget),
                scaled(spent), percent, scaled(budget.subtract(spent)));
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
