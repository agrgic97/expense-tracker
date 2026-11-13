package com.expensetracker.category;

import com.expensetracker.auth.CurrentUser;
import com.expensetracker.common.ConflictException;
import com.expensetracker.common.NotFoundException;
import com.expensetracker.expense.ExpenseRepository;
import com.expensetracker.recurring.RecurringExpenseRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private static final List<Category> DEFAULTS = List.of(
            new Category(null, "Lebensmittel", "#16a34a"),
            new Category(null, "Wohnen", "#2563eb"),
            new Category(null, "Transport", "#f59e0b"),
            new Category(null, "Restaurant", "#dc2626"),
            new Category(null, "Freizeit", "#9333ea"),
            new Category(null, "Gesundheit", "#0891b2"),
            new Category(null, "Kleidung", "#db2777"),
            new Category(null, "Abonnements", "#4f46e5"),
            new Category(null, "Sonstiges", "#64748b"),
            new Category(null, "Gehalt", "#059669", CategoryKind.INCOME),
            new Category(null, "Sonstige Einnahmen", "#0d9488", CategoryKind.INCOME));

    private final CategoryRepository categories;
    private final ExpenseRepository expenses;
    private final RecurringExpenseRepository recurring;
    private final CurrentUser currentUser;

    public CategoryService(CategoryRepository categories, ExpenseRepository expenses,
                           RecurringExpenseRepository recurring, CurrentUser currentUser) {
        this.categories = categories;
        this.expenses = expenses;
        this.recurring = recurring;
        this.currentUser = currentUser;
    }

    /**
     * Sets up the categories of a newly registered user. The very first user takes over all data that was
     * recorded before authentication existed; everyone else starts with the default categories.
     */
    @Transactional
    public void initializeFor(Long userId, boolean adoptUnownedData) {
        if (adoptUnownedData) {
            categories.adoptUnowned(userId);
            expenses.adoptUnowned(userId);
        }
        if (!categories.existsByUserId(userId)) {
            categories.saveAll(DEFAULTS.stream()
                    .map(c -> new Category(userId, c.getName(), c.getColor(), c.getKind()))
                    .toList());
        }
    }

    public List<CategoryDto> findAll() {
        return findAllEntities().stream().map(this::toDto).toList();
    }

    /** All categories of the current user, sorted by name. */
    public List<Category> findAllEntities() {
        return categories.findAllByUserId(currentUser.id()).stream()
                .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public Category get(Long id) {
        return categories.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Kategorie " + id + " nicht gefunden"));
    }

    @Transactional
    public CategoryDto create(CategoryRequest request) {
        ensureNameAvailable(request.name().trim(), null);
        return toDto(categories.save(
                new Category(currentUser.id(), request.name().trim(), request.color(), request.kindOrDefault())));
    }

    @Transactional
    public CategoryDto update(Long id, CategoryRequest request) {
        Category category = get(id);
        ensureNameAvailable(request.name().trim(), id);
        CategoryKind kind = request.kind() != null ? request.kind() : category.getKind();
        if (kind != category.getKind()) {
            // Switching would silently turn existing expenses into income (or vice versa)
            if (expenses.countByCategoryId(id) > 0 || recurring.countByCategoryId(id) > 0) {
                throw new ConflictException("Der Typ einer Kategorie kann nur geändert werden, solange sie nicht verwendet wird");
            }
            category.setKind(kind);
            if (kind == CategoryKind.INCOME) {
                category.setMonthlyBudget(null);
            }
        }
        category.setName(request.name().trim());
        category.setColor(request.color());
        return toDto(category);
    }

    /** Sets or, with {@code null}, removes the monthly budget of an expense category. */
    @Transactional
    public CategoryDto setBudget(Long id, BigDecimal monthlyBudget) {
        Category category = get(id);
        if (monthlyBudget != null && category.isIncome()) {
            throw new IllegalArgumentException("Budgets gibt es nur für Ausgabe-Kategorien");
        }
        category.setMonthlyBudget(monthlyBudget);
        return toDto(category);
    }

    @Transactional
    public void delete(Long id) {
        Category category = get(id);
        long count = expenses.countByCategoryId(id);
        if (count > 0) {
            throw new ConflictException(
                    "Kategorie '" + category.getName() + "' wird noch von " + count + " Buchung(en) verwendet");
        }
        long recurringCount = recurring.countByCategoryId(id);
        if (recurringCount > 0) {
            throw new ConflictException("Kategorie '" + category.getName() + "' wird noch von "
                    + recurringCount + " wiederkehrenden Buchung(en) verwendet");
        }
        categories.delete(category);
    }

    private void ensureNameAvailable(String name, Long ownId) {
        categories.findByUserIdAndNameIgnoreCase(currentUser.id(), name)
                .filter(existing -> !existing.getId().equals(ownId))
                .ifPresent(existing -> {
                    throw new ConflictException("Kategorie '" + name + "' existiert bereits");
                });
    }

    private CategoryDto toDto(Category category) {
        return new CategoryDto(category.getId(), category.getName(), category.getColor(), category.getKind(),
                category.getMonthlyBudget(),
                category.getId() == null ? 0 : expenses.countByCategoryId(category.getId()));
    }
}
