package com.exe.skillverse_backend.business_service.repository;

import com.exe.skillverse_backend.business_service.entity.RecruitmentSession;
import com.exe.skillverse_backend.business_service.entity.enums.RecruitmentJobContextType;
import com.exe.skillverse_backend.business_service.entity.enums.RecruitmentSessionSource;
import com.exe.skillverse_backend.business_service.entity.enums.RecruitmentSessionStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecruitmentSessionRepository extends JpaRepository<RecruitmentSession, Long> {

    /**
     * Tìm session dựa trên recruiter, candidate và job (nếu có)
     */
    Optional<RecruitmentSession> findByRecruiterIdAndCandidateIdAndJobPostingId(
            Long recruiterId, Long candidateId, Long jobPostingId);

    Optional<RecruitmentSession> findByRecruiterIdAndCandidateIdAndJobContextTypeAndJobContextId(
            Long recruiterId,
            Long candidateId,
            RecruitmentJobContextType jobContextType,
            Long jobContextId);

    /**
     * Tìm session dựa trên recruiter và candidate (không cần job)
     */
    Optional<RecruitmentSession> findByRecruiterIdAndCandidateId(
            Long recruiterId, Long candidateId);

    /**
     * Lấy tất cả session của một recruiter với phân trang
     */
    @Query("SELECT rs FROM RecruitmentSession rs " +
            "WHERE rs.recruiter.id = :recruiterId " +
            "AND rs.isArchivedByRecruiter = false " +
            "ORDER BY rs.lastMessageAt DESC NULLS LAST, rs.createdAt DESC")
    Page<RecruitmentSession> findByRecruiterId(
            @Param("recruiterId") Long recruiterId,
            Pageable pageable);

    /**
     * Lấy tất cả session của một recruiter với status cụ thể
     */
    @Query("SELECT rs FROM RecruitmentSession rs " +
            "WHERE rs.recruiter.id = :recruiterId " +
            "AND rs.status = :status " +
            "AND rs.isArchivedByRecruiter = false " +
            "ORDER BY rs.lastMessageAt DESC NULLS LAST")
    List<RecruitmentSession> findByRecruiterIdAndStatus(
            @Param("recruiterId") Long recruiterId,
            @Param("status") RecruitmentSessionStatus status);

    /**
     * Lấy tất cả session của một candidate với phân trang
     */
    @Query("SELECT rs FROM RecruitmentSession rs " +
            "WHERE rs.candidate.id = :candidateId " +
            "AND rs.isArchivedByCandidate = false " +
            "ORDER BY rs.lastMessageAt DESC NULLS LAST, rs.createdAt DESC")
    Page<RecruitmentSession> findByCandidateId(
            @Param("candidateId") Long candidateId,
            Pageable pageable);

    /**
     * Đếm số session chưa đọc của recruiter
     */
    @Query("SELECT COUNT(rs) FROM RecruitmentSession rs " +
            "WHERE rs.recruiter.id = :recruiterId " +
            "AND rs.unreadCountRecruiter > 0 " +
            "AND rs.isArchivedByRecruiter = false")
    long countUnreadByRecruiterId(@Param("recruiterId") Long recruiterId);

    /**
     * Đếm số session chưa đọc của candidate
     */
    @Query("SELECT COUNT(rs) FROM RecruitmentSession rs " +
            "WHERE rs.candidate.id = :candidateId " +
            "AND rs.unreadCountCandidate > 0 " +
            "AND rs.isArchivedByCandidate = false")
    long countUnreadByCandidateId(@Param("candidateId") Long candidateId);

    /**
     * Kiểm tra xem đã có session giữa recruiter và candidate cho job chưa
     */
    boolean existsByRecruiterIdAndCandidateIdAndJobPostingId(
            Long recruiterId, Long candidateId, Long jobPostingId);

    /**
     * Tìm session theo nguồn
     */
    @Query("SELECT rs FROM RecruitmentSession rs " +
            "WHERE rs.recruiter.id = :recruiterId " +
            "AND rs.sourceType = :sourceType " +
            "AND rs.isArchivedByRecruiter = false " +
            "ORDER BY rs.createdAt DESC")
    List<RecruitmentSession> findByRecruiterIdAndSourceType(
            @Param("recruiterId") Long recruiterId,
            @Param("sourceType") RecruitmentSessionSource sourceType);

    /**
     * Lấy sessions với job cụ thể
     */
    @Query("SELECT rs FROM RecruitmentSession rs " +
            "WHERE rs.recruiter.id = :recruiterId " +
            "AND rs.jobPosting.id = :jobId " +
            "AND rs.isArchivedByRecruiter = false " +
            "ORDER BY rs.lastMessageAt DESC NULLS LAST")
    List<RecruitmentSession> findByRecruiterIdAndJobPostingId(
            @Param("recruiterId") Long recruiterId,
            @Param("jobId") Long jobId);

    /**
     * Tìm kiếm session theo candidate name hoặc job title
     */
    @Query("SELECT rs FROM RecruitmentSession rs " +
            "WHERE rs.recruiter.id = :recruiterId " +
            "AND rs.isArchivedByRecruiter = false " +
            "AND (LOWER(CONCAT(rs.candidate.firstName, ' ', rs.candidate.lastName)) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(rs.jobTitle) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(rs.candidateTitle) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "ORDER BY rs.lastMessageAt DESC NULLS LAST")
    Page<RecruitmentSession> searchByRecruiterId(
            @Param("recruiterId") Long recruiterId,
            @Param("query") String query,
            Pageable pageable);

    /**
     * Detach recruitment sessions from a deleted job while keeping message history.
     */
    @Modifying
    @Query("UPDATE RecruitmentSession rs " +
            "SET rs.jobPosting = null, " +
            "rs.jobContextType = null, " +
            "rs.jobContextId = null, " +
            "rs.jobTitle = null, " +
            "rs.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE rs.jobPosting.id = :jobId")
    void clearJobPostingContext(@Param("jobId") Long jobId);
}
