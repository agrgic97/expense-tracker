package com.expensetracker.budget;

import com.expensetracker.budget.BudgetDtos.BudgetOverview;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Budgets are set per category via {@code PUT /api/categories/{id}/budget}; this reports how they are used. */
@RestController
@RequestMapping("/api/budgets")
public class BudgetController {

    private final BudgetService service;
    private final Clock clock;

    public BudgetController(BudgetService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping
    public BudgetOverview overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.overview(date != null ? date : LocalDate.now(clock));
    }
}
