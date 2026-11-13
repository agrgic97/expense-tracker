package com.expensetracker.recurring;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, Long> {

    @EntityGraph(attributePaths = "category")
    List<RecurringExpense> findAllByUserId(Long userId);

    /** Locks the row so the daily job and a user edit cannot book the same date twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RecurringExpense> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecurringExpense r where r.active = true and r.nextDate <= :today")
    List<RecurringExpense> findDue(@Param("today") LocalDate today);

    long countByCategoryId(Long categoryId);
}
