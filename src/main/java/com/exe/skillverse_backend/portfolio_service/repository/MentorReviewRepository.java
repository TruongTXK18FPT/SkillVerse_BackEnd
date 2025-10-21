package com.exe.skillverse_backend.portfolio_service.repository;

import com.exe.skillverse_backend.portfolio_service.entity.MentorReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MentorReviewRepository extends JpaRepository<MentorReview, Long> {
    
    List<MentorReview> findByUserIdAndIsPublicTrueOrderByCreatedAtDesc(Long userId);
    
    List<MentorReview> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    List<MentorReview> findByMentorIdOrderByCreatedAtDesc(Long mentorId);
    
    long countByUserId(Long userId);
    
    long countByUserIdAndIsVerifiedTrue(Long userId);
}
