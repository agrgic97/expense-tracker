package com.expensetracker.importer;

import com.expensetracker.auth.CurrentUser;
import com.expensetracker.category.Category;
import com.expensetracker.category.CategoryKind;
import com.expensetracker.category.CategoryService;
import com.expensetracker.common.NotFoundException;
import com.expensetracker.expense.Expense;
import com.expensetracker.expense.ExpenseRepository;
import com.expensetracker.expense.ExpenseRequest;
import com.expensetracker.importer.ImportDtos.PreviewRow;
import com.expensetracker.importer.ImportDtos.StatementRow;
import com.expensetracker.rule.CategoryRule;
import com.expensetracker.rule.CategoryRuleService;
import com.expensetracker.rule.RuleMatcher;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Imports bank statements that the client has already parsed and mapped. */
@Service
@Transactional(readOnly = true)
public class ImportService {

    private final ExpenseRepository expenses;
    private final CategoryService categories;
    private final CategoryRuleService rules;
    private final CurrentUser currentUser;

    public ImportService(ExpenseRepository expenses, CategoryService categories, CategoryRuleService rules,
                         CurrentUser currentUser) {
        this.expenses = expenses;
        this.categories = categories;
        this.rules = rules;
        this.currentUser = currentUser;
    }

    public List<PreviewRow> preview(List<StatementRow> rows) {
        RuleMatcher matcher = rules.matcher();
        LocalDate from = rows.stream().map(StatementRow::date).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = rows.stream().map(StatementRow::date).max(Comparator.naturalOrder()).orElseThrow();
        Set<String> booked = expenses.findByUserIdAndDateBetween(currentUser.id(), from, to).stream()
                .map(e -> key(e.getDate(), e.getAmount(), e.getDescription()))
                .collect(Collectors.toSet());

        return rows.stream()
                .map(row -> {
                    CategoryKind kind = row.amount().signum() < 0 ? CategoryKind.EXPENSE : CategoryKind.INCOME;
                    Long categoryId = matcher.match(row.description(), kind)
                            .map(CategoryRule::getCategory)
                            .map(Category::getId)
                            .orElse(null);
                    boolean duplicate = booked.contains(key(row.date(), row.amount().abs(), row.description()));
                    return new PreviewRow(categoryId, duplicate);
                })
                .toList();
    }

    @Transactional
    public int importRows(List<ExpenseRequest> rows) {
        Map<Long, Category> own = categories.findAllEntities().stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        List<Expense> entries = rows.stream()
                .map(row -> {
                    Category category = own.get(row.categoryId());
                    if (category == null) {
                        throw new NotFoundException("Kategorie " + row.categoryId() + " nicht gefunden");
                    }
                    return new Expense(currentUser.id(), row.amount(), row.date(), row.description().trim(), category);
                })
                .toList();
        expenses.saveAll(entries);
        return entries.size();
    }

    /** Identifies an entry independent of its category, so re-importing the same statement is recognized. */
    static String key(LocalDate date, BigDecimal amount, String description) {
        return date + "|" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString() + "|"
                + description.trim().replaceAll("\\s+", " ").toLowerCase(Locale.GERMAN);
    }
}
