package com.expensetracker.expense;

import com.expensetracker.category.CategoryKind;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseDto(Long id, BigDecimal amount, LocalDate date, String description,
                         Long categoryId, String categoryName, String categoryColor, CategoryKind kind,
                         Long recurringId) {

    public static ExpenseDto of(Expense expense) {
        return new ExpenseDto(expense.getId(), expense.getAmount(), expense.getDate(), expense.getDescription(),
                expense.getCategory().getId(), expense.getCategory().getName(), expense.getCategory().getColor(),
                expense.getCategory().getKind(), expense.getRecurringId());
    }
}
