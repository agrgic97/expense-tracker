package com.expensetracker.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "muss ein Hex-Farbwert wie #1a2b3c sein") String color) {
}
