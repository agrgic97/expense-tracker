package com.expensetracker.expense;

import java.time.LocalDate;

public record ExpenseFilter(LocalDate from, LocalDate to, Long categoryId, String q) {
}
