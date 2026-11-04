package com.expensetracker.category;

public record CategoryDto(Long id, String name, String color, long expenseCount) {
}
