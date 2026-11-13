package com.expensetracker.importer;

import com.expensetracker.expense.ExpenseRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ImportDtos {

    static final int MAX_ROWS = 5000;

    private ImportDtos() {
    }

    /** A row as read from a bank statement: negative amounts are expenses, positive ones income. */
    public record StatementRow(
            @NotNull LocalDate date,
            @NotNull @DecimalMin("-9999999999.99") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 255) String description) {
    }

    public record PreviewRequest(@NotEmpty @Size(max = MAX_ROWS) List<@Valid StatementRow> rows) {
    }

    /** Per input row, in the same order: the category a rule suggests (or null) and whether it is booked already. */
    public record PreviewRow(Long categoryId, boolean duplicate) {
    }

    public record PreviewResponse(List<PreviewRow> rows) {
    }

    public record ImportRequest(@NotEmpty @Size(max = MAX_ROWS) List<@Valid ExpenseRequest> rows) {
    }

    public record ImportResult(int imported) {
    }
}
