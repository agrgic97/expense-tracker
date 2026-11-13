package com.expensetracker.rule;

import com.expensetracker.category.CategoryKind;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Picks the rule for a description. Only rules whose category has the wanted kind are considered, and the
 * longest matching pattern wins, so "REWE Getränke" beats "REWE" without the user having to order rules.
 */
public final class RuleMatcher {

    private final List<CategoryRule> rules;

    public RuleMatcher(List<CategoryRule> rules) {
        this.rules = rules.stream()
                .sorted(Comparator.comparingInt((CategoryRule r) -> r.getPattern().length()).reversed()
                        .thenComparing(CategoryRule::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** {@code kind} null considers rules of both kinds. */
    public Optional<CategoryRule> match(String description, CategoryKind kind) {
        if (description == null || description.isBlank()) {
            return Optional.empty();
        }
        String text = normalize(description);
        return rules.stream()
                .filter(r -> kind == null || r.getCategory().getKind() == kind)
                .filter(r -> text.contains(normalize(r.getPattern())))
                .findFirst();
    }

    private static String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.GERMAN);
    }
}
