package com.exe.skillverse_backend.portfolio_service.repository;

import com.exe.skillverse_backend.portfolio_service.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // Find all products by user ID
    List<Product> findByUserIdOrderByCreatedAtDesc(Long userId);

    // Find products by user ID with pagination
    Page<Product> findByUserId(Long userId, Pageable pageable);

    // Find product by ID and user ID (for security)
    Optional<Product> findByIdAndUserId(Long id, Long userId);

    // Find products by category
    List<Product> findByUserIdAndCategoryIgnoreCase(Long userId, String category);

    // Find products released within a date range
    @Query("SELECT p FROM Product p WHERE p.userId = :userId AND p.releaseDate BETWEEN :startDate AND :endDate ORDER BY p.releaseDate DESC")
    List<Product> findByUserIdAndReleaseDateBetween(@Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // Find distinct categories for a user
    @Query("SELECT DISTINCT p.category FROM Product p WHERE p.userId = :userId AND p.category IS NOT NULL ORDER BY p.category")
    List<String> findDistinctCategoriesByUserId(@Param("userId") Long userId);

    // Count products by user
    long countByUserId(Long userId);

    // Find products with media
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.media WHERE p.userId = :userId ORDER BY p.createdAt DESC")
    List<Product> findByUserIdWithMedia(@Param("userId") Long userId);

    // Note: Complex flexible query methods have been moved to
    // ProductQueryBuilderService
    // for better maintainability and to eliminate long @Query annotations
}