package com.expensetracker.rule;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRuleRepository extends JpaRepository<CategoryRule, Long> {

    @EntityGraph(attributePaths = "category")
    List<CategoryRule> findAllByUserId(Long userId);

    Optional<CategoryRule> findByIdAndUserId(Long id, Long userId);

    Optional<CategoryRule> findByUserIdAndPatternIgnoreCase(Long userId, String pattern);
}
