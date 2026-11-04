package com.expensetracker.category;

import com.expensetracker.auth.CurrentUser;
import com.expensetracker.common.ConflictException;
import com.expensetracker.common.NotFoundException;
import com.expensetracker.expense.ExpenseRepository;
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
            new Category(null, "Sonstiges", "#64748b"));

    private final CategoryRepository categories;
    private final ExpenseRepository expenses;
    private final CurrentUser currentUser;

    public CategoryService(CategoryRepository categories, ExpenseRepository expenses, CurrentUser currentUser) {
        this.categories = categories;
        this.expenses = expenses;
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
                    .map(c -> new Category(userId, c.getName(), c.getColor()))
                    .toList());
        }
    }

    public List<CategoryDto> findAll() {
        return categories.findAllByUserId(currentUser.id()).stream()
                .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toDto)
                .toList();
    }

    public Category get(Long id) {
        return categories.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Kategorie " + id + " nicht gefunden"));
    }

    @Transactional
    public CategoryDto create(CategoryRequest request) {
        ensureNameAvailable(request.name().trim(), null);
        return toDto(categories.save(new Category(currentUser.id(), request.name().trim(), request.color())));
    }

    @Transactional
    public CategoryDto update(Long id, CategoryRequest request) {
        Category category = get(id);
        ensureNameAvailable(request.name().trim(), id);
        category.setName(request.name().trim());
        category.setColor(request.color());
        return toDto(category);
    }

    @Transactional
    public void delete(Long id) {
        Category category = get(id);
        long count = expenses.countByCategoryId(id);
        if (count > 0) {
            throw new ConflictException(
                    "Kategorie '" + category.getName() + "' wird noch von " + count + " Ausgabe(n) verwendet");
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
        return new CategoryDto(category.getId(), category.getName(), category.getColor(),
                category.getId() == null ? 0 : expenses.countByCategoryId(category.getId()));
    }
}
