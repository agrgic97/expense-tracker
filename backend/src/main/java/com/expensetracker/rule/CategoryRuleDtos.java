package com.expensetracker.rule;

import com.expensetracker.category.CategoryKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class CategoryRuleDtos {

    private CategoryRuleDtos() {
    }

    public record CategoryRuleRequest(@NotBlank @Size(max = 100) String pattern, @NotNull Long categoryId) {
    }

    public record CategoryRuleDto(Long id, String pattern, Long categoryId, String categoryName,
                                  String categoryColor, CategoryKind kind) {

        static CategoryRuleDto of(CategoryRule rule) {
            return new CategoryRuleDto(rule.getId(), rule.getPattern(), rule.getCategory().getId(),
                    rule.getCategory().getName(), rule.getCategory().getColor(), rule.getCategory().getKind());
        }
    }

    /** {@code categoryId} is null when no rule matches. */
    public record Suggestion(Long categoryId, Long ruleId) {
    }

    public record ApplyResult(int updated) {
    }
}
