package com.exe.skillverse_backend.mentor_verification_service.repository;

import com.exe.skillverse_backend.auth_service.entity.User;
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

    @Query("SELECT r FROM MentorSkillVerificationRequest r WHERE r.mentor.id = :mentorId AND r.skillName = :skillName AND r.status = 'APPROVED'")
    List<MentorSkillVerificationRequest> findApprovedByMentorIdAndSkillName(
            @Param("mentorId") Long mentorId,
            @Param("skillName") String skillName);

    /** Tìm tất cả mentor đã verify 1 skill cụ thể (APPROVED only).
     *  Separator-agnostic: strip ALL non-alphanumeric chars then UPPER, so
     *  "BACKEND", "BACK_END", "back end", "back-end" all collapse to "BACKEND".
     */
    @Query(value = """
            SELECT DISTINCT u.* FROM mentor_skill_verification_requests r
            JOIN users u ON u.id = r.mentor_id
            WHERE r.status = 'APPROVED'
              AND UPPER(REGEXP_REPLACE(r.skill_name, '[^a-zA-Z0-9]', '', 'g'))
                  = UPPER(REGEXP_REPLACE(:skillName,  '[^a-zA-Z0-9]', '', 'g'))
            """, nativeQuery = true)
    List<User> findMentorsByVerifiedSkill(@Param("skillName") String skillName);

    /** Kiểm tra mentor có verified skill này không (separator-agnostic). */
    @Query(value = """
            SELECT CASE WHEN COUNT(r.id) > 0 THEN TRUE ELSE FALSE END
            FROM mentor_skill_verification_requests r
            WHERE r.mentor_id = :mentorId
              AND r.status = 'APPROVED'
              AND UPPER(REGEXP_REPLACE(r.skill_name, '[^a-zA-Z0-9]', '', 'g'))
                  = UPPER(REGEXP_REPLACE(:skillName,  '[^a-zA-Z0-9]', '', 'g'))
            """, nativeQuery = true)
    boolean existsVerifiedSkill(@Param("mentorId") Long mentorId, @Param("skillName") String skillName);

    /** Đếm request pending */
    long countByStatus(VerificationStatus status);
}
