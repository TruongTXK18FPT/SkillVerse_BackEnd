package com.exe.skillverse_backend.mentor_verification_service.repository;

import com.exe.skillverse_backend.mentor_verification_service.entity.MentorSkillVerificationRequest;
import com.exe.skillverse_backend.mentor_verification_service.entity.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MentorSkillVerificationRequestRepository extends JpaRepository<MentorSkillVerificationRequest, Long> {

    /** Lấy tất cả request của 1 mentor */
    List<MentorSkillVerificationRequest> findByMentorIdOrderByRequestedAtDesc(Long mentorId);

    /** Admin: lấy danh sách chờ duyệt */
    Page<MentorSkillVerificationRequest> findByStatusOrderByRequestedAtAsc(VerificationStatus status, Pageable pageable);

    /** Admin: lấy tất cả (có filter status) */
    Page<MentorSkillVerificationRequest> findByStatusInOrderByRequestedAtDesc(List<VerificationStatus> statuses, Pageable pageable);

    /** Kiểm tra mentor đã có request PENDING hoặc APPROVED cho skill chưa */
    @Query("SELECT r FROM MentorSkillVerificationRequest r WHERE r.mentor.id = :mentorId AND r.skillName = :skillName AND r.status IN :statuses")
    Optional<MentorSkillVerificationRequest> findByMentorAndSkillAndStatusIn(
            @Param("mentorId") Long mentorId,
            @Param("skillName") String skillName,
            @Param("statuses") List<VerificationStatus> statuses);

    /** Lấy tất cả skill đã verified của mentor */
    @Query("SELECT r FROM MentorSkillVerificationRequest r WHERE r.mentor.id = :mentorId AND r.status = 'APPROVED'")
    List<MentorSkillVerificationRequest> findApprovedByMentorId(@Param("mentorId") Long mentorId);

    /** Đếm request pending */
    long countByStatus(VerificationStatus status);
}
