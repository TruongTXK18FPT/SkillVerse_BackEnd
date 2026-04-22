package com.exe.skillverse_backend.question_bank_service.repository;

import com.exe.skillverse_backend.question_bank_service.entity.QuestionBankSubmission;
import com.exe.skillverse_backend.question_bank_service.entity.enums.QuestionBankSubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionBankSubmissionRepository extends JpaRepository<QuestionBankSubmission, Long> {

    List<QuestionBankSubmission> findByMentorIdOrderByCreatedAtDesc(Long mentorId);

    Optional<QuestionBankSubmission> findByIdAndMentorId(Long id, Long mentorId);

    Page<QuestionBankSubmission> findByStatusInOrderByCreatedAtAsc(List<QuestionBankSubmissionStatus> statuses, Pageable pageable);

    long countByStatus(QuestionBankSubmissionStatus status);
}
