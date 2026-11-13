package com.expensetracker.rule;

import com.expensetracker.auth.CurrentUser;
import com.expensetracker.category.Category;
import com.expensetracker.category.CategoryKind;
import com.expensetracker.category.CategoryService;
import com.expensetracker.common.ConflictException;
import com.expensetracker.common.NotFoundException;
import com.expensetracker.expense.Expense;
import com.expensetracker.expense.ExpenseRepository;
import com.expensetracker.rule.CategoryRuleDtos.CategoryRuleDto;
import com.expensetracker.rule.CategoryRuleDtos.CategoryRuleRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CategoryRuleService {

    private final CategoryRuleRepository rules;
    private final ExpenseRepository expenses;
    private final CategoryService categories;
    private final CurrentUser currentUser;

    public CategoryRuleService(CategoryRuleRepository rules, ExpenseRepository expenses, CategoryService categories,
                               CurrentUser currentUser) {
        this.rules = rules;
        this.expenses = expenses;
        this.categories = categories;
        this.currentUser = currentUser;
    }

    public List<CategoryRuleDto> findAll() {
        return rules.findAllByUserId(currentUser.id()).stream()
                .sorted(Comparator.comparing(CategoryRule::getPattern, String.CASE_INSENSITIVE_ORDER))
                .map(CategoryRuleDto::of)
                .toList();
    }

    /** Matcher over all rules of the current user, for classifying many descriptions at once. */
    public RuleMatcher matcher() {
        return new RuleMatcher(rules.findAllByUserId(currentUser.id()));
    }

    public Optional<CategoryRule> suggest(String description, CategoryKind kind) {
        return matcher().match(description, kind);
    }

    @Transactional
    public CategoryRuleDto create(CategoryRuleRequest request) {
        String pattern = request.pattern().trim();
        ensurePatternAvailable(pattern, null);
        return CategoryRuleDto.of(rules.save(new CategoryRule(currentUser.id(), pattern, categories.get(request.categoryId()))));
    }

    @Transactional
    public CategoryRuleDto update(Long id, CategoryRuleRequest request) {
        CategoryRule rule = find(id);
        String pattern = request.pattern().trim();
        ensurePatternAvailable(pattern, id);
        rule.setPattern(pattern);
        rule.setCategory(categories.get(request.categoryId()));
        return CategoryRuleDto.of(rule);
    }

    @Transactional
    public void delete(Long id) {
        rules.delete(find(id));
    }

    /**
     * Recategorizes existing entries this rule matches. Entries for which a longer rule matches, and entries of the
     * other kind (an income never becomes an expense), are left alone.
     */
    @Transactional
    public int apply(Long id) {
        CategoryRule rule = find(id);
        Category target = rule.getCategory();
        RuleMatcher matcher = matcher();
        int updated = 0;
        for (Expense expense : expenses.findByUserIdAndDescriptionContainingIgnoreCase(currentUser.id(), rule.getPattern())) {
            boolean sameKind = expense.getCategory().getKind() == target.getKind();
            boolean bestMatch = matcher.match(expense.getDescription(), target.getKind())
                    .map(best -> best.getId().equals(rule.getId()))
                    .orElse(false);
            if (sameKind && bestMatch && !expense.getCategory().getId().equals(target.getId())) {
                expense.setCategory(target);
                updated++;
            }
        }
        return updated;
    }

    private void ensurePatternAvailable(String pattern, Long ownId) {
        rules.findByUserIdAndPatternIgnoreCase(currentUser.id(), pattern)
                .filter(existing -> !existing.getId().equals(ownId))
                .ifPresent(existing -> {
                    throw new ConflictException("Für '" + pattern + "' gibt es bereits eine Regel");
                });
    }

    private CategoryRule find(Long id) {
        return rules.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Regel " + id + " nicht gefunden"));
    }
}
