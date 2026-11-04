package com.expensetracker.expense;

import com.expensetracker.category.Category;
import com.expensetracker.category.CategoryService;
import com.expensetracker.common.NotFoundException;
import com.expensetracker.common.PageResponse;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ExpenseService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id"));

    private final ExpenseRepository expenses;
    private final CategoryService categories;

    public ExpenseService(ExpenseRepository expenses, CategoryService categories) {
        this.expenses = expenses;
        this.categories = categories;
    }

    public PageResponse<ExpenseDto> search(ExpenseFilter filter, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 200), NEWEST_FIRST);
        return PageResponse.of(expenses.findAll(ExpenseSpecifications.matching(filter), pageable), ExpenseDto::of);
    }

    public List<ExpenseDto> searchAll(ExpenseFilter filter) {
        return expenses.findAll(ExpenseSpecifications.matching(filter), NEWEST_FIRST).stream()
                .map(ExpenseDto::of)
                .toList();
    }

    public ExpenseDto get(Long id) {
        return ExpenseDto.of(find(id));
    }

    @Transactional
    public ExpenseDto create(ExpenseRequest request) {
        Category category = categories.get(request.categoryId());
        Expense expense = new Expense(request.amount(), request.date(), request.description().trim(), category);
        return ExpenseDto.of(expenses.save(expense));
    }

    @Transactional
    public ExpenseDto update(Long id, ExpenseRequest request) {
        Expense expense = find(id);
        expense.setAmount(request.amount());
        expense.setDate(request.date());
        expense.setDescription(request.description().trim());
        expense.setCategory(categories.get(request.categoryId()));
        return ExpenseDto.of(expense);
    }

    @Transactional
    public void delete(Long id) {
        expenses.delete(find(id));
    }

    private Expense find(Long id) {
        return expenses.findById(id)
                .orElseThrow(() -> new NotFoundException("Ausgabe " + id + " nicht gefunden"));
    }
}
