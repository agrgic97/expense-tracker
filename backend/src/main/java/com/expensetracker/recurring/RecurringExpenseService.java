package com.expensetracker.recurring;

import com.expensetracker.auth.CurrentUser;
import com.expensetracker.category.CategoryService;
import com.expensetracker.common.NotFoundException;
import com.expensetracker.expense.Expense;
import com.expensetracker.expense.ExpenseRepository;
import com.expensetracker.recurring.RecurringExpenseDtos.RecurringExpenseDto;
import com.expensetracker.recurring.RecurringExpenseDtos.RecurringExpenseRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RecurringExpenseService {

    private final RecurringExpenseRepository recurring;
    private final ExpenseRepository expenses;
    private final CategoryService categories;
    private final CurrentUser currentUser;
    private final Clock clock;

    public RecurringExpenseService(RecurringExpenseRepository recurring, ExpenseRepository expenses,
                                   CategoryService categories, CurrentUser currentUser, Clock clock) {
        this.recurring = recurring;
        this.expenses = expenses;
        this.categories = categories;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    public List<RecurringExpenseDto> findAll() {
        return recurring.findAllByUserId(currentUser.id()).stream()
                // Active ones by due date first, then paused and finished ones
                .sorted(Comparator.comparing((RecurringExpense r) -> !r.isActive() || r.getNextDate() == null)
                        .thenComparing(RecurringExpense::getNextDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(RecurringExpense::getDescription, String.CASE_INSENSITIVE_ORDER))
                .map(RecurringExpenseDto::of)
                .toList();
    }

    @Transactional
    public RecurringExpenseDto create(RecurringExpenseRequest request) {
        RecurringExpense template = new RecurringExpense(currentUser.id());
        apply(template, request);
        recurring.save(template);
        bookDue(template, LocalDate.now(clock));
        return RecurringExpenseDto.of(template);
    }

    @Transactional
    public RecurringExpenseDto update(Long id, RecurringExpenseRequest request) {
        RecurringExpense template = find(id);
        apply(template, request);
        bookDue(template, LocalDate.now(clock));
        return RecurringExpenseDto.of(template);
    }

    /** Deletes the template; entries booked from it are kept. */
    @Transactional
    public void delete(Long id) {
        recurring.delete(find(id));
    }

    /** Books every due entry of all users, including dates missed while the app was down. */
    @Transactional
    public int bookAllDue() {
        LocalDate today = LocalDate.now(clock);
        return recurring.findDue(today).stream().mapToInt(template -> bookDue(template, today)).sum();
    }

    private int bookDue(RecurringExpense template, LocalDate today) {
        int booked = 0;
        while (template.isActive() && template.getNextDate() != null && !template.getNextDate().isAfter(today)) {
            LocalDate date = template.getNextDate();
            expenses.save(Expense.fromRecurring(template.getUserId(), template.getAmount(), date,
                    template.getDescription(), template.getCategory(), template.getId()));
            template.markGenerated(date);
            booked++;
        }
        return booked;
    }

    private void apply(RecurringExpense template, RecurringExpenseRequest request) {
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("Das Enddatum darf nicht vor dem Startdatum liegen");
        }
        template.update(request.amount(), request.description().trim(), categories.get(request.categoryId()),
                request.recurrence(), request.startDate(), request.endDate(),
                request.active() == null || request.active(), LocalDate.now(clock));
    }

    private RecurringExpense find(Long id) {
        return recurring.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Wiederkehrende Buchung " + id + " nicht gefunden"));
    }
}
