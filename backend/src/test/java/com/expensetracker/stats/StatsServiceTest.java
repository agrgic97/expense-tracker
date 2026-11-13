package com.expensetracker.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.expensetracker.auth.CurrentUser;
import com.expensetracker.category.Category;
import com.expensetracker.category.CategoryKind;
import com.expensetracker.expense.Expense;
import com.expensetracker.expense.ExpenseRepository;
import com.expensetracker.stats.StatsDtos.Summary;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class StatsServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);
    private static final Long USER = 7L;

    private final ExpenseRepository repository = mock(ExpenseRepository.class);
    private StatsService service;
    private Category food;
    private Category rent;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        CurrentUser currentUser = mock(CurrentUser.class);
        when(currentUser.id()).thenReturn(USER);
        service = new StatsService(repository, currentUser, clock);
        food = category(1L, "Lebensmittel");
        rent = category(2L, "Wohnen");
    }

    @Test
    void summarizesRunningMonth() {
        when(repository.findAllInRange(USER, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))).thenReturn(List.of(
                expense("30.00", TODAY, food),
                expense("10.00", TODAY.minusDays(1), food),
                expense("800.00", LocalDate.of(2026, 11, 1), rent)));
        when(repository.sumInRange(USER, CategoryKind.EXPENSE, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .thenReturn(new BigDecimal("1000.00"));
        when(repository.sumInRange(USER, CategoryKind.EXPENSE, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 4)))
                .thenReturn(new BigDecimal("700.00"));

        Summary summary = service.summary(PeriodType.MONTH, TODAY);

        assertThat(summary.current()).isTrue();
        assertThat(summary.total()).isEqualByComparingTo("840.00");
        assertThat(summary.count()).isEqualTo(3);
        assertThat(summary.averagePerDay()).isEqualByComparingTo("210.00"); // 840 / 4 elapsed days
        assertThat(summary.projectedTotal()).isEqualByComparingTo("6300.00");
        assertThat(summary.previousComparableTotal()).isEqualByComparingTo("700.00");
        assertThat(summary.changePercent()).isEqualByComparingTo("20.0");
        assertThat(summary.breakdown()).hasSize(30);
        assertThat(summary.byCategory()).first().satisfies(c -> {
            assertThat(c.name()).isEqualTo("Wohnen");
            assertThat(c.sharePercent()).isEqualByComparingTo("95.2");
        });
        assertThat(summary.topExpenses()).first().satisfies(e -> assertThat(e.amount()).isEqualByComparingTo("800"));
    }

    @Test
    void separatesIncomeFromExpenses() {
        Category salary = category(3L, "Gehalt", CategoryKind.INCOME);
        when(repository.findAllInRange(eq(USER), any(), any())).thenReturn(List.of(
                expense("2000.00", LocalDate.of(2026, 10, 1), salary),
                expense("500.00", LocalDate.of(2026, 10, 2), rent),
                expense("1000.00", LocalDate.of(2026, 10, 3), food)));
        when(repository.sumInRange(eq(USER), eq(CategoryKind.EXPENSE), any(), any())).thenReturn(BigDecimal.ZERO);

        Summary summary = service.summary(PeriodType.MONTH, LocalDate.of(2026, 10, 15));

        assertThat(summary.total()).isEqualByComparingTo("1500.00");
        assertThat(summary.count()).isEqualTo(2);
        assertThat(summary.incomeTotal()).isEqualByComparingTo("2000.00");
        assertThat(summary.balance()).isEqualByComparingTo("500.00");
        assertThat(summary.savingsRatePercent()).isEqualByComparingTo("25.0");
        assertThat(summary.byCategory()).extracting(StatsDtos.CategoryTotal::name).containsExactly("Lebensmittel", "Wohnen");
        assertThat(summary.topExpenses()).hasSize(2);
        assertThat(summary.breakdown().getFirst().income()).isEqualByComparingTo("2000.00");
        assertThat(summary.breakdown().getFirst().total()).isEqualByComparingTo("0");
    }

    @Test
    void savingsRateIsNullWithoutIncome() {
        when(repository.findAllInRange(eq(USER), any(), any())).thenReturn(List.of(expense("10.00", TODAY, food)));
        when(repository.sumInRange(eq(USER), eq(CategoryKind.EXPENSE), any(), any())).thenReturn(BigDecimal.ZERO);

        Summary summary = service.summary(PeriodType.MONTH, TODAY);

        assertThat(summary.incomeTotal()).isEqualByComparingTo("0");
        assertThat(summary.balance()).isEqualByComparingTo("-10.00");
        assertThat(summary.savingsRatePercent()).isNull();
    }

    @Test
    void pastPeriodWithoutPreviousDataHasNoChange() {
        when(repository.findAllInRange(eq(USER), any(), any())).thenReturn(List.of(expense("70.00", LocalDate.of(2025, 3, 3), food)));
        when(repository.sumInRange(eq(USER), eq(CategoryKind.EXPENSE), any(), any())).thenReturn(BigDecimal.ZERO);

        Summary summary = service.summary(PeriodType.WEEK, LocalDate.of(2025, 3, 5));

        assertThat(summary.current()).isFalse();
        assertThat(summary.averagePerDay()).isEqualByComparingTo("10.00");
        assertThat(summary.projectedTotal()).isNull();
        assertThat(summary.changePercent()).isNull();
    }

    @Test
    void trendReturnsOneBucketPerPeriod() {
        when(repository.findAllInRange(eq(USER), eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 11, 30)))).thenReturn(List.of(
                expense("100.00", LocalDate.of(2026, 9, 10), food),
                expense("50.00", LocalDate.of(2026, 11, 2), food)));

        var trend = service.trend(PeriodType.MONTH, TODAY, 3);

        assertThat(trend.buckets()).extracting(StatsDtos.Bucket::total)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("50"));
        assertThat(trend.average()).isEqualByComparingTo("50.00");
        assertThat(trend.max()).isEqualByComparingTo("100.00");
    }

    private static Category category(Long id, String name) {
        return category(id, name, CategoryKind.EXPENSE);
    }

    private static Category category(Long id, String name, CategoryKind kind) {
        Category category = new Category(USER, name, "#000000", kind);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private static Expense expense(String amount, LocalDate date, Category category) {
        return new Expense(USER, new BigDecimal(amount), date, "Test", category);
    }
}
