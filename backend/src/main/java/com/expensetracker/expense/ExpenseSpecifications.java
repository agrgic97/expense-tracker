package com.expensetracker.expense;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

final class ExpenseSpecifications {

    private ExpenseSpecifications() {
    }

    static Specification<Expense> matching(Long userId, ExpenseFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("date"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<LocalDate>get("date"), filter.to()));
            }
            if (filter.categoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), filter.categoryId()));
            }
            if (filter.q() != null && !filter.q().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("description")),
                        "%" + filter.q().trim().toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
