package com.expensetracker.category;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByUserId(Long userId);

    Optional<Category> findByIdAndUserId(Long id, Long userId);

    Optional<Category> findByUserIdAndNameIgnoreCase(Long userId, String name);

    boolean existsByUserId(Long userId);

    /** Assigns categories created before authentication existed to the given user. */
    @Modifying
    @Query("update Category c set c.userId = :userId where c.userId is null")
    int adoptUnowned(@Param("userId") Long userId);
}
