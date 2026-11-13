package com.expensetracker.category;

import java.math.BigDecimal;

public record CategoryDto(Long id, String name, String color, CategoryKind kind, BigDecimal monthlyBudget,
                          long expenseCount) {
}
