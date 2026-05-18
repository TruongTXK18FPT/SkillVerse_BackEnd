package com.exe.skillverse_backend.mentor_verification_service.repository;

import com.exe.skillverse_backend.mentor_verification_service.entity.MentorBatchVerificationRequest;
import com.exe.skillverse_backend.mentor_verification_service.entity.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MentorBatchVerificationRequestRepository extends JpaRepository<MentorBatchVerificationRequest, Long> {

    List<MentorBatchVerificationRequest> findByMentorIdOrderBySubmittedAtDesc(Long mentorId);

    Page<MentorBatchVerificationRequest> findByStatusOrderBySubmittedAtAsc(VerificationStatus status, Pageable pageable);

    Page<MentorBatchVerificationRequest> findByStatusInOrderBySubmittedAtDesc(List<VerificationStatus> statuses, Pageable pageable);
}
