package com.expensetracker.expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    long countByCategoryId(Long categoryId);

    @Query("select e from Expense e join fetch e.category where e.date between :from and :to")
    List<Expense> findAllInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select coalesce(sum(e.amount), 0) from Expense e where e.date between :from and :to")
    BigDecimal sumInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Override
    @EntityGraph(attributePaths = "category")
    Page<Expense> findAll(Specification<Expense> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "category")
    List<Expense> findAll(Specification<Expense> spec, Sort sort);
}
