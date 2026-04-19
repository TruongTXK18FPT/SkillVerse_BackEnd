package com.exe.skillverse_backend.assignment_ai_service.event;

import com.exe.skillverse_backend.assignment_ai_service.service.AssignmentAiGradingService;
import com.exe.skillverse_backend.course_service.entity.Assignment;
import com.exe.skillverse_backend.course_service.entity.AssignmentSubmission;
import com.exe.skillverse_backend.course_service.repository.AssignmentRepository;
import com.exe.skillverse_backend.course_service.repository.AssignmentSubmissionRepository;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.dto.NotificationPayload;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for SubmissionCreatedEvent after the transaction commits.
 * Triggers AI grading asynchronously via gradingTaskExecutor.
 *
 * Key behaviors:
 * - AFTER_COMMIT: ensures submission is persisted before grading starts
 * - @Async: runs in separate thread to not block the HTTP response
 * - Idempotent: skips if already AI-graded or mentor-confirmed
 * - Dead letter: after 3 failed attempts, notify the student that manual mentor grading is needed
 * - Retry: failed submissions with attemptCount < 3 are picked up by AiGradingRetryJob
 * - 100% Auto-Pass: when AI grading succeeds, the grading service auto-confirms and persists the final score
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubmissionCreatedEventListener {

    private static final int MAX_AI_GRADE_ATTEMPTS = 3;

    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository submissionRepository;
    private final AssignmentAiGradingService aiGradingService;
    private final NotificationService notificationService;

    /**
     * Fires AFTER the transaction commits, so the submission is definitely persisted.
     * @Async makes this run in gradingTaskExecutor thread — the HTTP response returns immediately.
     */
    @Async("gradingTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmissionCreated(SubmissionCreatedEvent event) {
        Long submissionId = event.getSubmissionId();
        Long assignmentId = event.getAssignmentId();
        Long studentId = event.getStudentId();

        log.info("[AI-Grading] Received SubmissionCreatedEvent: submissionId={}, assignmentId={}",
                submissionId, assignmentId);

        try {
            processSubmission(submissionId, studentId);
        } catch (Exception e) {
            log.error("[AI-Grading] Unexpected error processing submission {}: {}",
                    submissionId, e.getMessage(), e);
            handleFailure(submissionId, studentId);
        }
    }

    private void processSubmission(Long submissionId, Long studentId) {
        try {
            AssignmentSubmission submission = submissionRepository.findByIdWithAssignment(submissionId)
                    .orElse(null);
            if (submission == null) {
                log.warn("[AI-Grading] Submission {} not found, skipping", submissionId);
                return;
            }

            // Idempotency: skip if already AI-graded
            if (Boolean.TRUE.equals(submission.getIsAiGraded())) {
                log.info("[AI-Grading] Submission {} already AI-graded, skipping", submissionId);
                return;
            }

            // Idempotency: skip if mentor already confirmed (e.g., manual grading happened)
            if (Boolean.TRUE.equals(submission.getMentorConfirmed())) {
                log.info("[AI-Grading] Submission {} mentor already confirmed, skipping", submissionId);
                return;
            }

            // Check if assignment has AI grading enabled
            Assignment assignment = submission.getAssignment();
            log.info("[AI-Grading] Checking assignment {}: aiGradingEnabled={}, trustAiEnabled={}, submissionIsAiGraded={}, submissionMentorConfirmed={}",
                    assignment.getId(), assignment.getAiGradingEnabled(), assignment.getTrustAiEnabled(),
                    submission.getIsAiGraded(), submission.getMentorConfirmed());
            if (!Boolean.TRUE.equals(assignment.getAiGradingEnabled())) {
                log.info("[AI-Grading] AI grading not enabled for assignment {}, skipping", assignment.getId());
                return;
            }

            // Attempt AI grading (idempotency is also checked inside the service)
            log.info("[AI-Grading] Triggering AI grading for submission {}", submissionId);
            aiGradingService.generateAiGrade(submissionId, null);

            log.info("[AI-Grading] AI grading completed for submission {}", submissionId);

        } catch (IllegalArgumentException e) {
            // Attempt cap reached or empty submission — don't retry, don't dead-letter
            log.warn("[AI-Grading] Non-retryable error for submission {}: {}", submissionId, e.getMessage());
        } catch (IllegalStateException e) {
            // AI not enabled or already graded — this is fine, just log
            log.info("[AI-Grading] Skipped for submission {}: {}", submissionId, e.getMessage());
        } catch (Exception e) {
            log.error("[AI-Grading] Unexpected error in processSubmission for submission {}: {}",
                    submissionId, e.getMessage(), e);
            handleFailure(submissionId, studentId);
        }
    }

    private void handleFailure(Long submissionId, Long studentId) {
        AssignmentSubmission submission = submissionRepository.findByIdWithAssignment(submissionId).orElse(null);
        if (submission == null) return;

        int attempts = submission.getAiGradeAttemptCount() != null
                ? submission.getAiGradeAttemptCount() : 0;

        if (attempts >= MAX_AI_GRADE_ATTEMPTS) {
            // Dead letter: mentor must grade manually
            log.warn("[AI-Grading] Dead letter for submission {} — {} attempts, mentor must grade manually",
                    submissionId, attempts);

            Assignment assignment = submission.getAssignment();
            notificationService.createNotification(
                    studentId,
                    "AI chấm bài không thành công",
                    "Bài tập '" + assignment.getTitle()
                            + "' sẽ được mentor chấm thủ công. Vui lòng chờ.",
                    NotificationType.ASSIGNMENT_GRADED,
                    submissionId.toString(),
                    NotificationPayload.forAssignmentGraded(assignment.getId(), submissionId),
                    null
            );
        } else {
            // Retry: AiGradingRetryJob will pick this up
            log.info("[AI-Grading] Submission {} failed (attempt {}/{}), will be retried",
                    submissionId, attempts, MAX_AI_GRADE_ATTEMPTS);
        }
    }
}