package com.expensetracker.recurring;

import com.expensetracker.category.CategoryKind;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class RecurringExpenseDtos {

    private RecurringExpenseDtos() {
    }

    /** {@code active} may be omitted and then defaults to true. */
    public record RecurringExpenseRequest(
            @NotNull @DecimalMin(value = "0.01") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 255) String description,
            @NotNull Long categoryId,
            @NotNull Recurrence recurrence,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            Boolean active) {
    }

    public record RecurringExpenseDto(Long id, BigDecimal amount, String description, Long categoryId,
                                      String categoryName, String categoryColor, CategoryKind kind,
                                      Recurrence recurrence, LocalDate startDate, LocalDate endDate, boolean active,
                                      LocalDate nextDate, LocalDate lastGeneratedDate) {

        static RecurringExpenseDto of(RecurringExpense r) {
            return new RecurringExpenseDto(r.getId(), r.getAmount(), r.getDescription(), r.getCategory().getId(),
                    r.getCategory().getName(), r.getCategory().getColor(), r.getCategory().getKind(),
                    r.getRecurrence(), r.getStartDate(), r.getEndDate(), r.isActive(), r.getNextDate(),
                    r.getLastGeneratedDate());
        }
    }
}
