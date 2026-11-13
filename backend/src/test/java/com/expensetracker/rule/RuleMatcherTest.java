package com.expensetracker.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.expensetracker.category.Category;
import com.expensetracker.category.CategoryKind;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RuleMatcherTest {

    private final Category food = new Category(1L, "Lebensmittel", "#000000");
    private final Category drinks = new Category(1L, "Getränke", "#000000");
    private final Category salary = new Category(1L, "Gehalt", "#000000", CategoryKind.INCOME);

    private final CategoryRule rewe = rule(1L, "REWE", food);
    private final CategoryRule reweDrinks = rule(2L, "rewe  getränke", drinks);
    private final CategoryRule employer = rule(3L, "ACME GmbH", salary);
    private final RuleMatcher matcher = new RuleMatcher(List.of(rewe, reweDrinks, employer));

    @Test
    void matchesCaseInsensitiveSubstring() {
        assertThat(matcher.match("Kartenzahlung rewe Markt 1234", CategoryKind.EXPENSE)).contains(rewe);
    }

    @Test
    void longestPatternWinsAndWhitespaceIsNormalized() {
        assertThat(matcher.match("REWE Getränke Abholmarkt", CategoryKind.EXPENSE)).contains(reweDrinks);
    }

    @Test
    void onlyConsidersRulesOfTheWantedKind() {
        assertThat(matcher.match("Gehalt ACME GmbH", CategoryKind.EXPENSE)).isEmpty();
        assertThat(matcher.match("Gehalt ACME GmbH", CategoryKind.INCOME)).contains(employer);
        assertThat(matcher.match("Gehalt ACME GmbH", null)).contains(employer);
    }

    @Test
    void noMatchForBlankOrUnknown() {
        assertThat(matcher.match("  ", CategoryKind.EXPENSE)).isEmpty();
        assertThat(matcher.match("Bäckerei", CategoryKind.EXPENSE)).isEmpty();
    }

    private static CategoryRule rule(Long id, String pattern, Category category) {
        CategoryRule rule = new CategoryRule(1L, pattern, category);
        ReflectionTestUtils.setField(rule, "id", id);
        return rule;
    }
}
