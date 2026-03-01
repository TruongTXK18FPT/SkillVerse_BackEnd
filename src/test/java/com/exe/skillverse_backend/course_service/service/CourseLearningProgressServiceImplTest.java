package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.course_service.dto.certificatedto.CertificateDTO;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.repository.AssignmentRepository;
import com.exe.skillverse_backend.course_service.repository.AssignmentSubmissionRepository;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.LessonProgressRepository;
import com.exe.skillverse_backend.course_service.repository.LessonRepository;
import com.exe.skillverse_backend.course_service.repository.QuizAttemptRepository;
import com.exe.skillverse_backend.course_service.repository.QuizRepository;
import com.exe.skillverse_backend.course_service.service.impl.CourseLearningProgressServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseLearningProgressServiceImplTest {

    @Mock
    private CourseEnrollmentRepository enrollmentRepository;

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private LessonProgressRepository lessonProgressRepository;

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private QuizAttemptRepository quizAttemptRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private AssignmentSubmissionRepository assignmentSubmissionRepository;

    @Mock
    private CertificateService certificateService;

    @InjectMocks
    private CourseLearningProgressServiceImpl courseLearningProgressService;

    @Test
    void recalculateCourseProgress_keepsCompletedEnrollmentWhenPercentDrops() {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStatus(EnrollmentStatus.COMPLETED);

        when(enrollmentRepository.findByCourseIdAndUserIdForUpdate(10L, 5L))
                .thenReturn(Optional.of(enrollment));
        when(lessonProgressRepository.findCompletedLessonIdsByCourseAndUser(10L, 5L))
                .thenReturn(List.of(1L));
        when(quizAttemptRepository.findPassedQuizIdsByCourseAndUser(10L, 5L)).thenReturn(List.of());
        when(assignmentSubmissionRepository.findPassedAssignmentIdsByCourseAndUser(10L, 5L))
                .thenReturn(List.of());
        when(assignmentSubmissionRepository.findPassedRequiredAssignmentIdsByCourseAndUser(10L, 5L))
                .thenReturn(List.of());
        when(lessonRepository.countByCourseId(10L)).thenReturn(2L);
        when(quizRepository.countByCourseId(10L)).thenReturn(0L);
        when(assignmentRepository.countRequiredByCourseId(10L)).thenReturn(0L);
        when(certificateService.findActiveUserCourseCertificate(10L, 5L))
                .thenReturn(Optional.empty());
        when(certificateService.findUserCourseCertificate(10L, 5L)).thenReturn(Optional.empty());

        int percent = courseLearningProgressService.recalculateCourseProgress(10L, 5L);

        assertEquals(50, percent);
        assertEquals(EnrollmentStatus.COMPLETED, enrollment.getStatus());
        assertEquals(50, enrollment.getProgressPercent());
        verify(certificateService, never()).issueCourseCertificate(eq(10L), eq(5L), any());
    }

    @Test
    void recalculateCourseProgress_issuesCertificateWhenCompletionReachesOneHundredPercent() {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStatus(EnrollmentStatus.ENROLLED);

        when(enrollmentRepository.findByCourseIdAndUserIdForUpdate(11L, 6L))
                .thenReturn(Optional.of(enrollment));
        when(lessonProgressRepository.findCompletedLessonIdsByCourseAndUser(11L, 6L))
                .thenReturn(List.of(1L));
        when(quizAttemptRepository.findPassedQuizIdsByCourseAndUser(11L, 6L))
                .thenReturn(List.of(2L));
        when(assignmentSubmissionRepository.findPassedAssignmentIdsByCourseAndUser(11L, 6L))
                .thenReturn(List.of(3L));
        when(assignmentSubmissionRepository.findPassedRequiredAssignmentIdsByCourseAndUser(11L, 6L))
                .thenReturn(List.of(3L));
        when(lessonRepository.countByCourseId(11L)).thenReturn(1L);
        when(quizRepository.countByCourseId(11L)).thenReturn(1L);
        when(assignmentRepository.countRequiredByCourseId(11L)).thenReturn(1L);
        when(certificateService.findActiveUserCourseCertificate(11L, 6L))
                .thenReturn(Optional.empty());
        when(certificateService.findUserCourseCertificate(11L, 6L)).thenReturn(Optional.empty());
        when(certificateService.issueCourseCertificate(eq(11L), eq(6L), any()))
                .thenReturn(new CertificateDTO());

        int percent = courseLearningProgressService.recalculateCourseProgress(11L, 6L);

        assertEquals(100, percent);
        assertEquals(EnrollmentStatus.COMPLETED, enrollment.getStatus());
        assertEquals(100, enrollment.getProgressPercent());
        verify(certificateService).issueCourseCertificate(eq(11L), eq(6L), any());
    }

    @Test
    void getCourseLearningStatus_marksRevokedCertificateWithoutExposingItAsActive() {
        CertificateDTO revokedCertificate = new CertificateDTO();
        revokedCertificate.setId(88L);
        revokedCertificate.setSerial("SV-C-12-U-7-REVOKED");
        revokedCertificate.setRevokedAt(Instant.parse("2026-02-28T08:00:00Z"));

        when(lessonProgressRepository.findCompletedLessonIdsByCourseAndUser(12L, 7L))
                .thenReturn(List.of());
        when(quizAttemptRepository.findPassedQuizIdsByCourseAndUser(12L, 7L)).thenReturn(List.of());
        when(assignmentSubmissionRepository.findPassedAssignmentIdsByCourseAndUser(12L, 7L))
                .thenReturn(List.of());
        when(assignmentSubmissionRepository.findPassedRequiredAssignmentIdsByCourseAndUser(12L, 7L))
                .thenReturn(List.of());
        when(lessonRepository.countByCourseId(12L)).thenReturn(0L);
        when(quizRepository.countByCourseId(12L)).thenReturn(0L);
        when(assignmentRepository.countRequiredByCourseId(12L)).thenReturn(0L);
        when(certificateService.findActiveUserCourseCertificate(12L, 7L))
                .thenReturn(Optional.empty());
        when(certificateService.findUserCourseCertificate(12L, 7L))
                .thenReturn(Optional.of(revokedCertificate));

        var status = courseLearningProgressService.getCourseLearningStatus(12L, 7L);

        assertNull(status.getCertificateId());
        assertEquals(Boolean.TRUE, status.getCertificateRevoked());
        assertEquals(revokedCertificate.getRevokedAt(), status.getCertificateRevokedAt());
    }
}
