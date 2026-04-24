package com.exe.skillverse_backend.student_skill_verification.repository;

import com.exe.skillverse_backend.student_skill_verification.entity.StudentSkillVerificationRequest;
import com.exe.skillverse_backend.student_skill_verification.entity.StudentVerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentSkillVerificationRequestRepository extends JpaRepository<StudentSkillVerificationRequest, Long> {

    List<StudentSkillVerificationRequest> findByUserIdOrderByRequestedAtDesc(Long userId);

    @Query("SELECT r FROM StudentSkillVerificationRequest r WHERE r.user.id = :userId AND r.status = 'APPROVED'")
    List<StudentSkillVerificationRequest> findApprovedByUserId(@Param("userId") Long userId);

    @Query("SELECT r FROM StudentSkillVerificationRequest r WHERE r.user.id = :userId AND r.skillName = :skillName AND r.status IN :statuses")
    Optional<StudentSkillVerificationRequest> findByUserAndSkillAndStatusIn(
            @Param("userId") Long userId,
            @Param("skillName") String skillName,
            @Param("statuses") List<StudentVerificationStatus> statuses);

    Page<StudentSkillVerificationRequest> findByStatusOrderByRequestedAtAsc(StudentVerificationStatus status, Pageable pageable);

    Page<StudentSkillVerificationRequest> findByStatusInOrderByRequestedAtDesc(List<StudentVerificationStatus> statuses, Pageable pageable);

    long countByStatus(StudentVerificationStatus status);
}
