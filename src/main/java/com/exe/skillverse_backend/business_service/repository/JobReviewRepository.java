package com.exe.skillverse_backend.business_service.repository;

import com.exe.skillverse_backend.business_service.entity.JobReview;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JobReviewRepository extends JpaRepository<JobReview, Long> {

    // Find by application
    List<JobReview> findByApplicationId(Long applicationId);

    // Find by reviewer
    List<JobReview> findByReviewerIdOrderByCreatedAtDesc(Long reviewerId);

    Page<JobReview> findByReviewerId(Long reviewerId, Pageable pageable);

    // Find by reviewee (person being reviewed)
    List<JobReview> findByRevieweeIdOrderByCreatedAtDesc(Long revieweeId);

    Page<JobReview> findByRevieweeId(Long revieweeId, Pageable pageable);

    // Find public reviews for a user
    @Query("SELECT r FROM JobReview r WHERE r.reviewee.id = :userId AND r.isPublic = true ORDER BY r.createdAt DESC")
    List<JobReview> findPublicReviewsForUser(@Param("userId") Long userId);

    @Query("SELECT r FROM JobReview r WHERE r.reviewee.id = :userId AND r.isPublic = true")
    Page<JobReview> findPublicReviewsForUser(@Param("userId") Long userId, Pageable pageable);

    // Check if review exists
    boolean existsByApplicationIdAndReviewerId(Long applicationId, Long reviewerId);

    Optional<JobReview> findByApplicationIdAndReviewerId(Long applicationId, Long reviewerId);

    // Calculate average rating for user
    @Query("SELECT AVG(r.rating) FROM JobReview r WHERE r.reviewee.id = :userId AND r.isPublic = true")
    BigDecimal getAverageRatingForUser(@Param("userId") Long userId);

    // Count reviews
    long countByRevieweeId(Long revieweeId);

    @Query("SELECT COUNT(r) FROM JobReview r WHERE r.reviewee.id = :userId AND r.isPublic = true")
    long countPublicReviewsForUser(@Param("userId") Long userId);

    // Rating breakdown
    @Query("SELECT r.rating, COUNT(r) FROM JobReview r WHERE r.reviewee.id = :userId AND r.isPublic = true GROUP BY r.rating")
    List<Object[]> getRatingBreakdownForUser(@Param("userId") Long userId);

    // Average specific ratings
    @Query("SELECT AVG(r.communicationRating), AVG(r.qualityRating), AVG(r.timelinessRating), AVG(r.professionalismRating) " +
            "FROM JobReview r WHERE r.reviewee.id = :userId AND r.isPublic = true")
    Object[] getAverageSpecificRatingsForUser(@Param("userId") Long userId);
}
