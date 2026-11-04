package com.expensetracker.category;

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

    private final CategoryRepository categories;
    private final ExpenseRepository expenses;

    public CategoryService(CategoryRepository categories, ExpenseRepository expenses) {
        this.categories = categories;
        this.expenses = expenses;
    }

    public List<CategoryDto> findAll() {
        return categories.findAll().stream()
                .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toDto)
                .toList();
    }

    public Category get(Long id) {
        return categories.findById(id)
                .orElseThrow(() -> new NotFoundException("Kategorie " + id + " nicht gefunden"));
    }

    @Transactional
    public CategoryDto create(CategoryRequest request) {
        ensureNameAvailable(request.name().trim(), null);
        return toDto(categories.save(new Category(request.name().trim(), request.color())));
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
        categories.findByNameIgnoreCase(name)
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
