package com.expensetracker.expense;

import com.expensetracker.category.CategoryKind;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    long countByCategoryId(Long categoryId);

    Optional<Expense> findByIdAndUserId(Long id, Long userId);

    @Query("select e from Expense e join fetch e.category where e.userId = :userId and e.date between :from and :to")
    List<Expense> findAllInRange(@Param("userId") Long userId, @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);

    @Query("select coalesce(sum(e.amount), 0) from Expense e "
            + "where e.userId = :userId and e.category.kind = :kind and e.date between :from and :to")
    BigDecimal sumInRange(@Param("userId") Long userId, @Param("kind") CategoryKind kind,
                          @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Expense totals per category in a date range, as [categoryId, sum] rows. */
    @Query("select e.category.id, sum(e.amount) from Expense e "
            + "where e.userId = :userId and e.category.kind = com.expensetracker.category.CategoryKind.EXPENSE "
            + "and e.date between :from and :to group by e.category.id")
    List<Object[]> sumByCategoryInRange(@Param("userId") Long userId, @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);

    @EntityGraph(attributePaths = "category")
    List<Expense> findByUserIdAndDescriptionContainingIgnoreCase(Long userId, String text);

    List<Expense> findByUserIdAndDateBetween(Long userId, LocalDate from, LocalDate to);

    /** Assigns expenses created before authentication existed to the given user. */
    @Modifying
    @Query("update Expense e set e.userId = :userId where e.userId is null")
    int adoptUnowned(@Param("userId") Long userId);

    @Override
    @EntityGraph(attributePaths = "category")
    Page<Expense> findAll(Specification<Expense> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "category")
    List<Expense> findAll(Specification<Expense> spec, Sort sort);
}
