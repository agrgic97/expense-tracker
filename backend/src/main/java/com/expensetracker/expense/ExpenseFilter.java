package com.expensetracker.expense;

import com.expensetracker.category.CategoryKind;
import java.time.LocalDate;

/** All fields are optional; {@code kind} null matches expenses and income. */
public record ExpenseFilter(LocalDate from, LocalDate to, Long categoryId, String q, CategoryKind kind) {
}
