package com.expensetracker.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.expensetracker.auth.CurrentUser;
import com.expensetracker.expense.ExpenseRepository;
import com.expensetracker.recurring.RecurringExpenseRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CategoryServiceTest {

    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final ExpenseRepository expenses = mock(ExpenseRepository.class);
    private final CategoryService service = new CategoryService(categories, expenses,
            mock(RecurringExpenseRepository.class), mock(CurrentUser.class));

    @Test
    void newUserGetsExpenseAndIncomeDefaults() {
        when(categories.findByUserIdAndNameIgnoreCase(eq(2L), any())).thenReturn(Optional.empty());

        service.initializeFor(2L, false);

        assertThat(saved()).extracting(Category::getKind).contains(CategoryKind.EXPENSE, CategoryKind.INCOME);
    }

    @Test
    void firstUserAdoptingOldExpenseCategoriesStillGetsIncomeCategories() {
        when(categories.existsByUserIdAndKind(1L, CategoryKind.EXPENSE)).thenReturn(true);
        when(categories.findByUserIdAndNameIgnoreCase(eq(1L), any())).thenReturn(Optional.empty());

        service.initializeFor(1L, true);

        verify(categories).adoptUnowned(1L);
        verify(expenses).adoptUnowned(1L);
        assertThat(saved()).extracting(Category::getName).containsExactly("Gehalt", "Sonstige Einnahmen");
    }

    @Test
    void addsNothingWhenBothKindsExist() {
        when(categories.existsByUserIdAndKind(eq(1L), any())).thenReturn(true);

        service.initializeFor(1L, false);

        verify(categories, never()).saveAll(any());
    }

    @SuppressWarnings("unchecked")
    private List<Category> saved() {
        ArgumentCaptor<List<Category>> captor = ArgumentCaptor.forClass(List.class);
        verify(categories, org.mockito.Mockito.atLeastOnce()).saveAll(captor.capture());
        return captor.getAllValues().stream().flatMap(List::stream).toList();
    }
}
