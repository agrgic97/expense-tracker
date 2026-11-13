package com.expensetracker.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.expensetracker.auth.CurrentUser;
import com.expensetracker.category.Category;
import com.expensetracker.category.CategoryKind;
import com.expensetracker.category.CategoryService;
import com.expensetracker.expense.Expense;
import com.expensetracker.expense.ExpenseRepository;
import com.expensetracker.importer.ImportDtos.PreviewRow;
import com.expensetracker.importer.ImportDtos.StatementRow;
import com.expensetracker.rule.CategoryRule;
import com.expensetracker.rule.CategoryRuleService;
import com.expensetracker.rule.RuleMatcher;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ImportServiceTest {

    private static final Long USER = 7L;
    private static final LocalDate DAY = LocalDate.of(2026, 11, 3);

    private final ExpenseRepository expenses = mock(ExpenseRepository.class);
    private final CategoryRuleService rules = mock(CategoryRuleService.class);

    @Test
    void suggestsCategoriesByKindAndFlagsBookedRows() {
        Category food = category(1L, "Lebensmittel", CategoryKind.EXPENSE);
        Category salary = category(2L, "Gehalt", CategoryKind.INCOME);
        CategoryRule reweRule = new CategoryRule(USER, "REWE", food);
        ReflectionTestUtils.setField(reweRule, "id", 1L);
        CategoryRule acmeRule = new CategoryRule(USER, "ACME", salary);
        ReflectionTestUtils.setField(acmeRule, "id", 2L);
        when(rules.matcher()).thenReturn(new RuleMatcher(List.of(reweRule, acmeRule)));
        when(expenses.findByUserIdAndDateBetween(USER, DAY, DAY.plusDays(1))).thenReturn(List.of(
                new Expense(USER, new BigDecimal("12.5"), DAY, "REWE  Markt", food)));
        CurrentUser currentUser = mock(CurrentUser.class);
        when(currentUser.id()).thenReturn(USER);
        ImportService service = new ImportService(expenses, mock(CategoryService.class), rules, currentUser);

        List<PreviewRow> preview = service.preview(List.of(
                new StatementRow(DAY, new BigDecimal("-12.50"), "rewe markt"),
                new StatementRow(DAY.plusDays(1), new BigDecimal("-12.50"), "REWE Markt"),
                new StatementRow(DAY, new BigDecimal("2500.00"), "Lohn ACME"),
                new StatementRow(DAY, new BigDecimal("-30.00"), "ACME Shop")));

        assertThat(preview).containsExactly(
                new PreviewRow(1L, true),
                new PreviewRow(1L, false),
                new PreviewRow(2L, false),
                new PreviewRow(null, false));
    }

    private static Category category(Long id, String name, CategoryKind kind) {
        Category category = new Category(USER, name, "#000000", kind);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }
}
