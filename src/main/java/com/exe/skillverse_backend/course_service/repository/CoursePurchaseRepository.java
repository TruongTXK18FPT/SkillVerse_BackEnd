package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.CoursePurchase;
import com.exe.skillverse_backend.course_service.entity.enums.PurchaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface CoursePurchaseRepository extends JpaRepository<CoursePurchase, Long> {

    /**
     * Find purchases by user ID with pagination
     */
    @Transactional(readOnly = true)
    @Query("SELECT cp FROM CoursePurchase cp WHERE cp.user.id = :userId")
    Page<CoursePurchase> findByUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * Sum captured/completed purchases for a course
     */
    @Transactional(readOnly = true)
    @Query("SELECT SUM(cp.price) FROM CoursePurchase cp WHERE cp.course.id = :courseId AND cp.status = 'COMPLETED'")
    Optional<BigDecimal> sumCapturedByCourse(@Param("courseId") Long courseId);

    /**
     * Find purchases by course ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT cp FROM CoursePurchase cp WHERE cp.course.id = :courseId")
    Page<CoursePurchase> findByCourseId(@Param("courseId") Long courseId, Pageable pageable);

    /**
     * Find purchases by status
     */
    @Transactional(readOnly = true)
    @Query("SELECT cp FROM CoursePurchase cp WHERE cp.status = :status")
    Page<CoursePurchase> findByStatus(@Param("status") PurchaseStatus status, Pageable pageable);

    /**
     * Find user's purchase for specific course
     */
    @Transactional(readOnly = true)
    @Query("SELECT cp FROM CoursePurchase cp WHERE cp.user.id = :userId AND cp.course.id = :courseId")
    Optional<CoursePurchase> findByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);

    /**
     * Check if user has purchased course
     */
    @Transactional(readOnly = true)
    @Query("SELECT CASE WHEN COUNT(cp) > 0 THEN true ELSE false END " +
           "FROM CoursePurchase cp WHERE cp.user.id = :userId AND cp.course.id = :courseId AND cp.status = 'COMPLETED'")
    boolean hasUserPurchasedCourse(@Param("userId") Long userId, @Param("courseId") Long courseId);

    /**
     * Count successful purchases for course
     */
    @Transactional(readOnly = true)
    @Query("SELECT COUNT(cp) FROM CoursePurchase cp WHERE cp.course.id = :courseId AND cp.status = 'COMPLETED'")
    long countSuccessfulPurchasesByCourseId(@Param("courseId") Long courseId);

    /**
     * Find recent purchases (last 30 days)
     */
    @Transactional(readOnly = true)
    @Query("SELECT cp FROM CoursePurchase cp WHERE cp.purchasedAt >= :since ORDER BY cp.purchasedAt DESC")
    List<CoursePurchase> findRecentPurchases(@Param("since") java.time.Instant since);
}
