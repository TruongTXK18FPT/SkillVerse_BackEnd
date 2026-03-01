package com.exe.skillverse_backend.course_service.service.impl;

import com.exe.skillverse_backend.course_service.dto.certificatedto.CertificateDTO;
import com.exe.skillverse_backend.course_service.dto.progressdto.CourseLearningStatusDTO;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.repository.AssignmentRepository;
import com.exe.skillverse_backend.course_service.repository.AssignmentSubmissionRepository;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.LessonProgressRepository;
import com.exe.skillverse_backend.course_service.repository.LessonRepository;
import com.exe.skillverse_backend.course_service.repository.QuizAttemptRepository;
import com.exe.skillverse_backend.course_service.repository.QuizRepository;
import com.exe.skillverse_backend.course_service.service.CertificateService;
import com.exe.skillverse_backend.course_service.service.CourseLearningProgressService;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseLearningProgressServiceImpl implements CourseLearningProgressService {

    private static final String ENROLLMENT_NOT_FOUND = "ENROLLMENT_NOT_FOUND";

    private final CourseEnrollmentRepository enrollmentRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final QuizRepository quizRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository assignmentSubmissionRepository;
    private final CertificateService certificateService;

    @Override
    @Transactional(readOnly = true)
    public CourseLearningStatusDTO getCourseLearningStatus(Long courseId, Long userId) {
        List<Long> completedLessonIds = lessonProgressRepository.findCompletedLessonIdsByCourseAndUser(courseId, userId);
        List<Long> completedQuizIds = quizAttemptRepository.findPassedQuizIdsByCourseAndUser(courseId, userId);
        List<Long> completedAssignmentIds = assignmentSubmissionRepository
                .findPassedAssignmentIdsByCourseAndUser(courseId, userId);
        List<Long> completedRequiredAssignmentIds = assignmentSubmissionRepository
                .findPassedRequiredAssignmentIdsByCourseAndUser(courseId, userId);

        long totalLessonCount = lessonRepository.countByCourseId(courseId);
        long totalQuizCount = quizRepository.countByCourseId(courseId);
        long totalRequiredAssignmentCount = assignmentRepository.countRequiredByCourseId(courseId);

        long completedLessonCount = completedLessonIds.size();
        long completedQuizCount = completedQuizIds.size();
        long completedRequiredAssignmentCount = completedRequiredAssignmentIds.size();

        long totalItemCount = totalLessonCount + totalQuizCount + totalRequiredAssignmentCount;
        long completedItemCount = completedLessonCount + completedQuizCount + completedRequiredAssignmentCount;
        int percent = totalItemCount == 0
                ? 0
                : Math.min(100, (int) Math.round((completedItemCount * 100.0) / totalItemCount));
        CertificateDTO activeCertificate = certificateService
                .findActiveUserCourseCertificate(courseId, userId)
                .orElse(null);
        CertificateDTO latestCertificate = certificateService.findUserCourseCertificate(courseId, userId).orElse(null);
        boolean certificateRevoked = activeCertificate == null
                && latestCertificate != null
                && latestCertificate.getRevokedAt() != null;
        Instant certificateRevokedAt = certificateRevoked ? latestCertificate.getRevokedAt() : null;

        return CourseLearningStatusDTO.builder()
                .courseId(courseId)
                .userId(userId)
                .completedLessonIds(completedLessonIds)
                .completedQuizIds(completedQuizIds)
                .completedAssignmentIds(completedAssignmentIds)
                .completedLessonCount(completedLessonCount)
                .totalLessonCount(totalLessonCount)
                .completedQuizCount(completedQuizCount)
                .totalQuizCount(totalQuizCount)
                .completedRequiredAssignmentCount(completedRequiredAssignmentCount)
                .totalRequiredAssignmentCount(totalRequiredAssignmentCount)
                .completedItemCount(completedItemCount)
                .totalItemCount(totalItemCount)
                .percent(percent)
                .certificateId(activeCertificate != null ? activeCertificate.getId() : null)
                .certificateSerial(activeCertificate != null ? activeCertificate.getSerial() : null)
                .certificateRevoked(certificateRevoked)
                .certificateRevokedAt(certificateRevokedAt)
                .build();
    }

    @Override
    @Transactional
    public int recalculateCourseProgress(Long courseId, Long userId) {
        CourseEnrollment enrollment = enrollmentRepository.findByCourseIdAndUserIdForUpdate(courseId, userId)
                .orElseThrow(() -> new NotFoundException(ENROLLMENT_NOT_FOUND));

        CourseLearningStatusDTO status = getCourseLearningStatus(courseId, userId);
        enrollment.setProgressPercent(status.getPercent());

        if (enrollment.getStatus() == null) {
            enrollment.setStatus(EnrollmentStatus.ENROLLED);
        }

        if (status.getPercent() >= 100 && enrollment.getStatus() != EnrollmentStatus.DROPPED) {
            enrollment.setStatus(EnrollmentStatus.COMPLETED);
            certificateService.issueCourseCertificate(courseId, userId, status);
        }

        enrollmentRepository.save(enrollment);

        log.debug(
                "Recalculated course {} progress for user {} to {}%",
                courseId,
                userId,
                status.getPercent()
        );
        return status.getPercent();
    }
}
