package com.exe.skillverse_backend.student_verification_service.repository;

import com.exe.skillverse_backend.student_verification_service.entity.StudentVerificationRequest;
import com.exe.skillverse_backend.student_verification_service.enums.StudentVerificationStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentVerificationRequestRepository extends JpaRepository<StudentVerificationRequest, Long> {

    Optional<StudentVerificationRequest> findByIdAndUser_Id(Long requestId, Long userId);

    Optional<StudentVerificationRequest> findTopByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<StudentVerificationRequest> findTopByUser_IdAndStatusOrderByReviewedAtDesc(
            Long userId,
            StudentVerificationStatus status
    );

    boolean existsByUser_IdAndStatus(Long userId, StudentVerificationStatus status);

    boolean existsBySchoolEmailAndStatusAndUser_IdNot(
            String schoolEmail,
            StudentVerificationStatus status,
            Long userId
    );

    Optional<StudentVerificationRequest> findTopBySchoolEmailAndStatusOrderByReviewedAtDesc(
            String schoolEmail,
            StudentVerificationStatus status
    );

    Page<StudentVerificationRequest> findByStatusOrderByCreatedAtDesc(
            StudentVerificationStatus status,
            Pageable pageable
    );

    Page<StudentVerificationRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
