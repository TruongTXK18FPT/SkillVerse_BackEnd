package com.exe.skillverse_backend.mentor_service.repository;

import com.exe.skillverse_backend.mentor_service.entity.ApplicationStatus;
import com.exe.skillverse_backend.mentor_service.entity.MentorProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MentorProfileRepository extends JpaRepository<MentorProfile, Long> {

    Optional<MentorProfile> findByUserId(Long userId);

    List<MentorProfile> findByApplicationStatus(ApplicationStatus status);

    boolean existsByUserId(Long userId);

    /**
     * Find mentors who have submitted CCCD (cccdExtractedData is populated)
     * but have NOT yet been identity verified by admin.
     * These are the ones admin needs to review.
     * Excludes: null, empty, processing state, and error state.
     */
    @Query("SELECT m FROM MentorProfile m WHERE (m.identityVerified IS NULL OR m.identityVerified = false) " +
           "AND m.cccdExtractedData IS NOT NULL " +
           "AND LENGTH(m.cccdExtractedData) > 5 " +
           "AND m.cccdExtractedData NOT LIKE '%\"status\":\"processing\"%' " +
           "AND m.cccdExtractedData NOT LIKE '%\"status\":\"error\"%' " +
           "ORDER BY m.updatedAt DESC")
    List<MentorProfile> findPendingCccdVerifications();

    List<MentorProfile> findByCccdNumber(String cccdNumber);
}