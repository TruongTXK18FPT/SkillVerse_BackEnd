package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.dto.certificatedto.CertificateDTO;
import com.exe.skillverse_backend.course_service.dto.progressdto.CourseLearningRevisionInfoDTO;
import com.exe.skillverse_backend.course_service.entity.Assignment;
import com.exe.skillverse_backend.course_service.entity.AssignmentSubmission;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.course_service.entity.CourseRevision;
import com.exe.skillverse_backend.course_service.entity.Lesson;
import com.exe.skillverse_backend.course_service.entity.enums.CourseUpgradePolicy;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.entity.enums.QuizAttemptSessionStatus;
import com.exe.skillverse_backend.course_service.repository.AssignmentRepository;
import com.exe.skillverse_backend.course_service.repository.AssignmentSubmissionRepository;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRevisionRepository;
import com.exe.skillverse_backend.course_service.repository.LessonProgressRepository;
import com.exe.skillverse_backend.course_service.repository.LessonRepository;
import com.exe.skillverse_backend.course_service.repository.QuizAttemptRepository;
import com.exe.skillverse_backend.course_service.repository.QuizAttemptSessionRepository;
import com.exe.skillverse_backend.course_service.repository.QuizRepository;
import com.exe.skillverse_backend.course_service.service.impl.CourseLearningProgressServiceImpl;
import com.exe.skillverse_backend.shared.exception.ConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseLearningProgressServiceImplTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


    @Mock
    private CourseEnrollmentRepository enrollmentRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseRevisionRepository courseRevisionRepository;

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private LessonProgressRepository lessonProgressRepository;

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private QuizAttemptRepository quizAttemptRepository;

    @Mock
    private QuizAttemptSessionRepository quizAttemptSessionRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private AssignmentSubmissionRepository assignmentSubmissionRepository;

    @Mock
    private CertificateService certificateService;

    @Mock
    private MeterRegistry meterRegistry;

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

    @Test
    void getCourseLearningStatus_usesPinnedRevisionSnapshotWhenAvailable() throws Exception {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setLearningRevisionId(700L);

        CourseRevision revision = CourseRevision.builder()
                .id(700L)
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "snapshotVersion": 1,
                          "modules": [
                            {
                              "id": 1,
                              "lessons": [ { "id": 10 }, { "id": 11 } ],
                              "quizzes": [ { "id": 21 } ],
                              "assignments": [
                                { "id": 31, "isRequired": true },
                                { "id": 32, "isRequired": false }
                              ]
                            }
                          ]
                        }
                        """))
                .build();

        when(enrollmentRepository.findByCourseIdAndUserId(13L, 8L)).thenReturn(Optional.of(enrollment));
        when(courseRevisionRepository.findByIdAndCourse_Id(700L, 13L)).thenReturn(Optional.of(revision));
        when(lessonProgressRepository.findCompletedLessonIdsByCourseAndUser(13L, 8L))
                .thenReturn(List.of(10L, 99L));
        when(quizAttemptRepository.findPassedQuizIdsByCourseAndUser(13L, 8L))
                .thenReturn(List.of(21L, 77L));
        when(assignmentSubmissionRepository.findPassedAssignmentIdsByCourseAndUser(13L, 8L))
                .thenReturn(List.of(31L, 32L, 1000L));
        when(assignmentSubmissionRepository.findPassedRequiredAssignmentIdsByCourseAndUser(13L, 8L))
                .thenReturn(List.of(31L, 1000L));
        when(certificateService.findActiveUserCourseCertificate(13L, 8L)).thenReturn(Optional.empty());
        when(certificateService.findUserCourseCertificate(13L, 8L)).thenReturn(Optional.empty());

        var status = courseLearningProgressService.getCourseLearningStatus(13L, 8L);

        assertEquals(2, status.getTotalLessonCount());
        assertEquals(1, status.getTotalQuizCount());
        assertEquals(1, status.getTotalRequiredAssignmentCount());
        assertEquals(1, status.getCompletedLessonCount());
        assertEquals(1, status.getCompletedQuizCount());
        assertEquals(1, status.getCompletedRequiredAssignmentCount());
        assertEquals(75, status.getPercent());
        assertEquals(List.of(10L), status.getCompletedLessonIds());
        assertEquals(List.of(21L), status.getCompletedQuizIds());
        assertEquals(List.of(31L, 32L), status.getCompletedAssignmentIds());
        verify(lessonRepository, never()).countByCourseId(13L);
        verify(quizRepository, never()).countByCourseId(13L);
        verify(assignmentRepository, never()).countRequiredByCourseId(13L);
    }

    @Test
    void getCourseLearningStatus_fallsBackToLegacyWhenRevisionSnapshotMissing() {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setLearningRevisionId(701L);

        when(enrollmentRepository.findByCourseIdAndUserId(14L, 9L)).thenReturn(Optional.of(enrollment));
        when(courseRevisionRepository.findByIdAndCourse_Id(701L, 14L)).thenReturn(Optional.empty());
        when(lessonProgressRepository.findCompletedLessonIdsByCourseAndUser(14L, 9L))
                .thenReturn(List.of(10L));
        when(quizAttemptRepository.findPassedQuizIdsByCourseAndUser(14L, 9L))
                .thenReturn(List.of(20L));
        when(assignmentSubmissionRepository.findPassedAssignmentIdsByCourseAndUser(14L, 9L))
                .thenReturn(List.of(30L));
        when(assignmentSubmissionRepository.findPassedRequiredAssignmentIdsByCourseAndUser(14L, 9L))
                .thenReturn(List.of(30L));
        when(lessonRepository.countByCourseId(14L)).thenReturn(3L);
        when(quizRepository.countByCourseId(14L)).thenReturn(2L);
        when(assignmentRepository.countRequiredByCourseId(14L)).thenReturn(1L);
        when(certificateService.findActiveUserCourseCertificate(14L, 9L)).thenReturn(Optional.empty());
        when(certificateService.findUserCourseCertificate(14L, 9L)).thenReturn(Optional.empty());

        var status = courseLearningProgressService.getCourseLearningStatus(14L, 9L);

        assertEquals(3, status.getTotalLessonCount());
        assertEquals(2, status.getTotalQuizCount());
        assertEquals(1, status.getTotalRequiredAssignmentCount());
        assertEquals(3, status.getCompletedItemCount());
        assertEquals(6, status.getTotalItemCount());
        assertEquals(50, status.getPercent());
        verify(lessonRepository).countByCourseId(14L);
        verify(quizRepository).countByCourseId(14L);
        verify(assignmentRepository).countRequiredByCourseId(14L);
    }

    @Test
    void getCourseLearningStatus_fallsBackToLegacyWhenSnapshotVersionMissing() throws Exception {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setLearningRevisionId(702L);

        CourseRevision revision = CourseRevision.builder()
                .id(702L)
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "modules": [
                            { "id": 1, "lessons": [ { "id": 10 } ] }
                          ]
                        }
                        """))
                .build();

        when(enrollmentRepository.findByCourseIdAndUserId(15L, 9L)).thenReturn(Optional.of(enrollment));
        when(courseRevisionRepository.findByIdAndCourse_Id(702L, 15L)).thenReturn(Optional.of(revision));
        when(lessonProgressRepository.findCompletedLessonIdsByCourseAndUser(15L, 9L))
                .thenReturn(List.of(10L));
        when(quizAttemptRepository.findPassedQuizIdsByCourseAndUser(15L, 9L)).thenReturn(List.of());
        when(assignmentSubmissionRepository.findPassedAssignmentIdsByCourseAndUser(15L, 9L))
                .thenReturn(List.of());
        when(assignmentSubmissionRepository.findPassedRequiredAssignmentIdsByCourseAndUser(15L, 9L))
                .thenReturn(List.of());
        when(lessonRepository.countByCourseId(15L)).thenReturn(4L);
        when(quizRepository.countByCourseId(15L)).thenReturn(0L);
        when(assignmentRepository.countRequiredByCourseId(15L)).thenReturn(0L);
        when(certificateService.findActiveUserCourseCertificate(15L, 9L)).thenReturn(Optional.empty());
        when(certificateService.findUserCourseCertificate(15L, 9L)).thenReturn(Optional.empty());

        var status = courseLearningProgressService.getCourseLearningStatus(15L, 9L);

        assertEquals(4, status.getTotalLessonCount());
        assertEquals(25, status.getPercent());
        verify(lessonRepository).countByCourseId(15L);
    }

    @Test
    void getCourseLearningStatus_fallsBackToLegacyWhenSnapshotVersionUnsupported() throws Exception {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setLearningRevisionId(703L);

        CourseRevision revision = CourseRevision.builder()
                .id(703L)
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "snapshotVersion": 2,
                          "modules": [
                            { "id": 1, "lessons": [ { "id": 10 } ] }
                          ]
                        }
                        """))
                .build();

        when(enrollmentRepository.findByCourseIdAndUserId(16L, 10L)).thenReturn(Optional.of(enrollment));
        when(courseRevisionRepository.findByIdAndCourse_Id(703L, 16L)).thenReturn(Optional.of(revision));
        when(lessonProgressRepository.findCompletedLessonIdsByCourseAndUser(16L, 10L))
                .thenReturn(List.of(10L));
        when(quizAttemptRepository.findPassedQuizIdsByCourseAndUser(16L, 10L)).thenReturn(List.of());
        when(assignmentSubmissionRepository.findPassedAssignmentIdsByCourseAndUser(16L, 10L))
                .thenReturn(List.of());
        when(assignmentSubmissionRepository.findPassedRequiredAssignmentIdsByCourseAndUser(16L, 10L))
                .thenReturn(List.of());
        when(lessonRepository.countByCourseId(16L)).thenReturn(2L);
        when(quizRepository.countByCourseId(16L)).thenReturn(0L);
        when(assignmentRepository.countRequiredByCourseId(16L)).thenReturn(0L);
        when(certificateService.findActiveUserCourseCertificate(16L, 10L)).thenReturn(Optional.empty());
        when(certificateService.findUserCourseCertificate(16L, 10L)).thenReturn(Optional.empty());

        var status = courseLearningProgressService.getCourseLearningStatus(16L, 10L);

        assertEquals(2, status.getTotalLessonCount());
        assertEquals(50, status.getPercent());
        verify(lessonRepository).countByCourseId(16L);
    }

    @Test
    void getCourseLearningStatus_failClosesWhenRevisionEnabledAndSnapshotMissing() {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setLearningRevisionId(704L);

        Course revisionEnabledCourse = new Course();
        revisionEnabledCourse.setId(17L);
        revisionEnabledCourse.setRevisioningEnabled(true);

        when(courseRepository.findById(17L)).thenReturn(Optional.of(revisionEnabledCourse));
        when(enrollmentRepository.findByCourseIdAndUserId(17L, 10L)).thenReturn(Optional.of(enrollment));
        when(courseRevisionRepository.findByIdAndCourse_Id(704L, 17L)).thenReturn(Optional.empty());
        when(lessonProgressRepository.findCompletedLessonIdsByCourseAndUser(17L, 10L))
                .thenReturn(List.of(10L));
        when(quizAttemptRepository.findPassedQuizIdsByCourseAndUser(17L, 10L))
                .thenReturn(List.of(20L));
        when(assignmentSubmissionRepository.findPassedAssignmentIdsByCourseAndUser(17L, 10L))
                .thenReturn(List.of(30L));
        when(assignmentSubmissionRepository.findPassedRequiredAssignmentIdsByCourseAndUser(17L, 10L))
                .thenReturn(List.of(30L));
        when(certificateService.findActiveUserCourseCertificate(17L, 10L)).thenReturn(Optional.empty());
        when(certificateService.findUserCourseCertificate(17L, 10L)).thenReturn(Optional.empty());

        var status = courseLearningProgressService.getCourseLearningStatus(17L, 10L);

        assertEquals(0, status.getTotalItemCount());
        assertEquals(0, status.getCompletedItemCount());
        assertEquals(0, status.getPercent());
        assertEquals(List.of(), status.getLegacyQuizResults());
        assertEquals(List.of(), status.getLegacyAssignmentResults());
        verify(lessonRepository, never()).countByCourseId(17L);
        verify(quizRepository, never()).countByCourseId(17L);
        verify(assignmentRepository, never()).countRequiredByCourseId(17L);
    }

    @Test
    void getCourseLearningStatus_ignoresLegacyQuizHistoryQueryError() throws Exception {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setLearningRevisionId(705L);

        CourseRevision revision = CourseRevision.builder()
                .id(705L)
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "snapshotVersion": 1,
                          "modules": [
                            {
                              "id": 1,
                              "lessons": [],
                              "quizzes": [ { "id": 21 } ],
                              "assignments": []
                            }
                          ]
                        }
                        """))
                .build();

        when(enrollmentRepository.findByCourseIdAndUserId(18L, 11L)).thenReturn(Optional.of(enrollment));
        when(courseRevisionRepository.findByIdAndCourse_Id(705L, 18L)).thenReturn(Optional.of(revision));
        when(lessonProgressRepository.findCompletedLessonIdsByCourseAndUser(18L, 11L))
                .thenReturn(List.of());
        when(quizAttemptRepository.findPassedQuizIdsByCourseAndUser(18L, 11L))
                .thenReturn(List.of(21L));
        when(assignmentSubmissionRepository.findPassedAssignmentIdsByCourseAndUser(18L, 11L))
                .thenReturn(List.of());
        when(assignmentSubmissionRepository.findPassedRequiredAssignmentIdsByCourseAndUser(18L, 11L))
                .thenReturn(List.of());
        when(quizAttemptRepository.findPassedQuizAttemptSummariesByCourseAndUser(18L, 11L))
                .thenThrow(new NullPointerException("hibernate_bug_repro"));
        when(certificateService.findActiveUserCourseCertificate(18L, 11L)).thenReturn(Optional.empty());
        when(certificateService.findUserCourseCertificate(18L, 11L)).thenReturn(Optional.empty());

        var status = courseLearningProgressService.getCourseLearningStatus(18L, 11L);

        assertEquals(1, status.getTotalQuizCount());
        assertEquals(1, status.getCompletedQuizCount());
        assertEquals(100, status.getPercent());
        assertEquals(List.of(), status.getLegacyQuizResults());
    }

                @Test
                void getCourseLearningStatus_degradesGracefullyWhenLegacyQuizProjectionAndFallbackBothFail() throws Exception {
                                CourseEnrollment enrollment = new CourseEnrollment();
                                enrollment.setLearningRevisionId(7051L);

                                CourseRevision revision = CourseRevision.builder()
                                                                .id(7051L)
                                                                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                                                                                                {
                                                                                                        "snapshotVersion": 1,
                                                                                                        "modules": [
                                                                                                                {
                                                                                                                        "id": 1,
                                                                                                                        "lessons": [],
                                                                                                                        "quizzes": [ { "id": 21 } ],
                                                                                                                        "assignments": []
                                                                                                                }
                                                                                                        ]
                                                                                                }
                                                                                                """))
                                                                .build();

                                when(enrollmentRepository.findByCourseIdAndUserId(18001L, 1101L)).thenReturn(Optional.of(enrollment));
                                when(courseRevisionRepository.findByIdAndCourse_Id(7051L, 18001L)).thenReturn(Optional.of(revision));
                                when(lessonProgressRepository.findCompletedLessonIdsByCourseAndUser(18001L, 1101L)).thenReturn(List.of());
                                when(quizAttemptRepository.findPassedQuizIdsByCourseAndUser(18001L, 1101L)).thenReturn(List.of(21L));
                                when(assignmentSubmissionRepository.findPassedAssignmentIdsByCourseAndUser(18001L, 1101L)).thenReturn(List.of());
                                when(assignmentSubmissionRepository.findPassedRequiredAssignmentIdsByCourseAndUser(18001L, 1101L)).thenReturn(List.of());
                                when(quizAttemptRepository.findPassedQuizAttemptSummariesByCourseAndUser(18001L, 1101L))
                                                                .thenThrow(new IllegalStateException("projection_error"));
                                when(quizAttemptRepository.findPassedQuizAttemptsByCourseAndUser(18001L, 1101L))
                                                                .thenThrow(new IllegalStateException("entity_fallback_error"));
                                when(certificateService.findActiveUserCourseCertificate(18001L, 1101L)).thenReturn(Optional.empty());
                                when(certificateService.findUserCourseCertificate(18001L, 1101L)).thenReturn(Optional.empty());

                                var status = courseLearningProgressService.getCourseLearningStatus(18001L, 1101L);

                                assertEquals(1, status.getTotalQuizCount());
                                assertEquals(1, status.getCompletedQuizCount());
                                assertEquals(100, status.getPercent());
                                assertEquals(List.of(), status.getLegacyQuizResults());
                }

                @Test
                void getCourseLearningStatus_manyAttemptsPerfGuard_reportsBeforeAfterLocally() throws Exception {
                                CourseEnrollment enrollment = new CourseEnrollment();
                                enrollment.setLearningRevisionId(7052L);

                                CourseRevision revision = CourseRevision.builder()
                                                                .id(7052L)
                                                                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                                                                                                {
                                                                                                        "snapshotVersion": 1,
                                                                                                        "modules": [
                                                                                                                {
                                                                                                                        "id": 1,
                                                                                                                        "lessons": [],
                                                                                                                        "quizzes": [ { "id": 21 } ],
                                                                                                                        "assignments": []
                                                                                                                }
                                                                                                        ]
                                                                                                }
                                                                                                """))
                                                                .build();

                                List<QuizAttemptRepository.PassedQuizAttemptSummary> beforeLikeSummaries = new ArrayList<>();
                                List<QuizAttemptRepository.PassedQuizAttemptSummary> afterLikeSummaries = new ArrayList<>();
                                Instant anchor = Instant.parse("2026-01-11T10:00:00Z");

                                for (long quizId = 20000L; quizId < 20200L; quizId++) {
                                                QuizAttemptRepository.PassedQuizAttemptSummary summary = mock(QuizAttemptRepository.PassedQuizAttemptSummary.class);
                                        when(summary.getQuizId()).thenReturn(quizId);
                                        when(summary.getQuizTitle()).thenReturn("Quiz " + quizId);
                                                when(summary.getScore()).thenReturn(70);
                                        when(summary.getSubmittedAt()).thenReturn(anchor.minusSeconds(quizId));
                                        afterLikeSummaries.add(summary);
                                }

                                for (long quizId = 20000L; quizId < 20200L; quizId++) {
                                        for (int attemptOffset = 0; attemptOffset < 30; attemptOffset++) {
                                                QuizAttemptRepository.PassedQuizAttemptSummary summary = mock(QuizAttemptRepository.PassedQuizAttemptSummary.class);
                                                when(summary.getQuizId()).thenReturn(quizId);
                                                when(summary.getQuizTitle()).thenReturn("Quiz " + quizId);
                                                when(summary.getScore()).thenReturn(70);
                                                when(summary.getSubmittedAt()).thenReturn(anchor.minusSeconds(quizId + attemptOffset));
                                                beforeLikeSummaries.add(summary);
                                        }
                                }

                                when(enrollmentRepository.findByCourseIdAndUserId(18002L, 1102L)).thenReturn(Optional.of(enrollment));
                                when(courseRevisionRepository.findByIdAndCourse_Id(7052L, 18002L)).thenReturn(Optional.of(revision));
                                when(lessonProgressRepository.findCompletedLessonIdsByCourseAndUser(18002L, 1102L)).thenReturn(List.of());
                                when(quizAttemptRepository.findPassedQuizIdsByCourseAndUser(18002L, 1102L)).thenReturn(List.of(21L));
                                when(assignmentSubmissionRepository.findPassedAssignmentIdsByCourseAndUser(18002L, 1102L)).thenReturn(List.of());
                                when(assignmentSubmissionRepository.findPassedRequiredAssignmentIdsByCourseAndUser(18002L, 1102L)).thenReturn(List.of());
                                when(quizAttemptRepository.findPassedQuizAttemptSummariesByCourseAndUser(18002L, 1102L))
                                        .thenReturn(beforeLikeSummaries)
                                        .thenReturn(afterLikeSummaries);
                                when(assignmentSubmissionRepository.findLatestPassedNewestByCourseAndUser(18002L, 1102L)).thenReturn(List.of());
                                when(certificateService.findActiveUserCourseCertificate(18002L, 1102L)).thenReturn(Optional.empty());
                                when(certificateService.findUserCourseCertificate(18002L, 1102L)).thenReturn(Optional.empty());

                                Instant beforeStart = Instant.now();
                                var beforeLikeStatus = courseLearningProgressService.getCourseLearningStatus(18002L, 1102L);
                                long beforeMs = Duration.between(beforeStart, Instant.now()).toMillis();

                                Instant afterStart = Instant.now();
                                var afterLikeStatus = courseLearningProgressService.getCourseLearningStatus(18002L, 1102L);
                                long afterMs = Duration.between(afterStart, Instant.now()).toMillis();

                                assertEquals(20, beforeLikeStatus.getLegacyQuizResults().size());
                                assertEquals(20, afterLikeStatus.getLegacyQuizResults().size());
                                assertEquals(Boolean.TRUE, beforeLikeStatus.getLegacyQuizResultsHasMore());
                                assertEquals(Boolean.TRUE, afterLikeStatus.getLegacyQuizResultsHasMore());
                                assertTrue(beforeMs < 3000, "Expected before-like local perf guard under 3000ms, actual=" + beforeMs);
                                assertTrue(afterMs < 3000, "Expected after-like local perf guard under 3000ms, actual=" + afterMs);

                                System.out.println("[LOCAL_BENCH] history_before_ms=" + beforeMs + " history_after_ms=" + afterMs
                                        + " before_payload_size=" + beforeLikeSummaries.size()
                                        + " after_payload_size=" + afterLikeSummaries.size());
                }

    @Test
    void getCourseLearningStatus_includesLegacyMetadataAndImpactedItemsForRevisionScopedHistory() throws Exception {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setLearningRevisionId(706L);

        Course course = new Course();
        course.setId(1810L);
        course.setActiveRevisionId(707L);

        CourseRevision revision = CourseRevision.builder()
                .id(706L)
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "snapshotVersion": 1,
                          "modules": [
                            {
                              "id": 1,
                              "lessons": [ { "id": 10 } ],
                              "quizzes": [ { "id": 21 } ],
                              "assignments": [ { "id": 31, "isRequired": true } ]
                            }
                          ]
                        }
                        """))
                .build();

        QuizAttemptRepository.PassedQuizAttemptSummary legacyQuiz = mock(QuizAttemptRepository.PassedQuizAttemptSummary.class);
        when(legacyQuiz.getQuizId()).thenReturn(22L);
        when(legacyQuiz.getQuizTitle()).thenReturn("Legacy Quiz");
        when(legacyQuiz.getScore()).thenReturn(85);
        when(legacyQuiz.getSubmittedAt()).thenReturn(Instant.parse("2026-01-10T10:00:00Z"));

        Assignment assignment = new Assignment();
        assignment.setId(32L);
        assignment.setTitle("Legacy Assignment");

        AssignmentSubmission legacySubmission = new AssignmentSubmission();
        legacySubmission.setAssignment(assignment);
        legacySubmission.setScore(new BigDecimal("7.50"));
        legacySubmission.setSubmittedAt(Instant.parse("2026-01-09T10:00:00Z"));

        Lesson legacyLesson = new Lesson();
        legacyLesson.setId(99L);
        legacyLesson.setTitle("Legacy Lesson");

        when(courseRepository.findById(1810L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByCourseIdAndUserId(1810L, 81L)).thenReturn(Optional.of(enrollment));
        when(courseRevisionRepository.findByIdAndCourse_Id(706L, 1810L)).thenReturn(Optional.of(revision));
        when(lessonProgressRepository.findCompletedLessonIdsByCourseAndUser(1810L, 81L))
                .thenReturn(List.of(10L, 99L));
        when(quizAttemptRepository.findPassedQuizIdsByCourseAndUser(1810L, 81L))
                .thenReturn(List.of(21L, 22L));
        when(assignmentSubmissionRepository.findPassedAssignmentIdsByCourseAndUser(1810L, 81L))
                .thenReturn(List.of(31L, 32L));
        when(assignmentSubmissionRepository.findPassedRequiredAssignmentIdsByCourseAndUser(1810L, 81L))
                .thenReturn(List.of(31L, 32L));
        when(quizAttemptRepository.findPassedQuizAttemptSummariesByCourseAndUser(1810L, 81L))
                .thenReturn(List.of(legacyQuiz));
        when(assignmentSubmissionRepository.findLatestPassedNewestByCourseAndUser(1810L, 81L))
                .thenReturn(List.of(legacySubmission));
        when(lessonRepository.findAllById(anyCollection())).thenReturn(List.of(legacyLesson));
        when(certificateService.findActiveUserCourseCertificate(1810L, 81L)).thenReturn(Optional.empty());
        when(certificateService.findUserCourseCertificate(1810L, 81L)).thenReturn(Optional.empty());

        var status = courseLearningProgressService.getCourseLearningStatus(1810L, 81L);

        assertEquals(1, status.getLegacyQuizResults().size());
        assertEquals(706L, status.getLegacyQuizResults().get(0).getSourceRevisionId());
        assertEquals("Item does not exist in the active revision.", status.getLegacyQuizResults().get(0).getReason());
        assertEquals(Boolean.TRUE, status.getLegacyQuizResults().get(0).getIsBreakingChanged());
        assertEquals("ITEM_NOT_IN_ACTIVE_REVISION", status.getLegacyQuizResults().get(0).getBreakingReason());
        assertEquals(Boolean.TRUE, status.getLegacyQuizResults().get(0).getRequiresRetake());

        assertEquals(1, status.getLegacyAssignmentResults().size());
        assertEquals(706L, status.getLegacyAssignmentResults().get(0).getSourceRevisionId());
        assertEquals("Item does not exist in the active revision.", status.getLegacyAssignmentResults().get(0).getReason());
        assertEquals(Boolean.TRUE, status.getLegacyAssignmentResults().get(0).getIsBreakingChanged());
        assertEquals("ITEM_NOT_IN_ACTIVE_REVISION", status.getLegacyAssignmentResults().get(0).getBreakingReason());
        assertEquals(Boolean.TRUE, status.getLegacyAssignmentResults().get(0).getRequiresRetake());

        assertEquals(Boolean.FALSE, status.getLegacyQuizResultsHasMore());
        assertEquals(Boolean.FALSE, status.getLegacyAssignmentResultsHasMore());
        assertEquals(20, status.getLegacyHistoryLimit());

        assertEquals(3, status.getImpactedItems().size());
        assertTrue(status.getImpactedItems().stream().anyMatch(item -> "QUIZ".equals(item.getItemType()) && item.getItemId().equals(22L)));
        assertTrue(status.getImpactedItems().stream().anyMatch(item -> "ASSIGNMENT".equals(item.getItemType()) && item.getItemId().equals(32L)));
        assertTrue(status.getImpactedItems().stream().anyMatch(item -> "LESSON".equals(item.getItemType()) && item.getItemId().equals(99L)));
        assertTrue(status.getImpactedItems().stream().anyMatch(item ->
                "ASSIGNMENT".equals(item.getItemType())
                        && item.getItemId().equals(32L)
                        && Boolean.TRUE.equals(item.getIsBreakingChanged())
                        && Boolean.TRUE.equals(item.getRequiresRetake())
                        && "ITEM_NOT_IN_ACTIVE_REVISION".equals(item.getBreakingReason())));
    }

    @Test
    void getCourseLearningStatus_marksBreakingAssignmentMetadataForImpactedAssignment() throws Exception {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setLearningRevisionId(7061L);

        Course course = new Course();
        course.setId(1812L);
        course.setActiveRevisionId(7062L);

        CourseRevision revision = CourseRevision.builder()
                .id(7061L)
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "snapshotVersion": 1,
                          "modules": [
                            {
                              "id": 1,
                              "lessons": [],
                              "quizzes": [],
                              "assignments": [ { "id": 31, "isRequired": true } ]
                            }
                          ]
                        }
                        """))
                .build();

        Assignment assignment = new Assignment();
        assignment.setId(32L);
        assignment.setTitle("Legacy Assignment");

        AssignmentSubmission legacySubmission = new AssignmentSubmission();
        legacySubmission.setAssignment(assignment);
        legacySubmission.setScore(new BigDecimal("9.00"));
        legacySubmission.setSubmittedAt(Instant.parse("2026-01-11T10:00:00Z"));

        when(courseRepository.findById(1812L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByCourseIdAndUserId(1812L, 83L)).thenReturn(Optional.of(enrollment));
        when(courseRevisionRepository.findByIdAndCourse_Id(7061L, 1812L)).thenReturn(Optional.of(revision));
        when(lessonProgressRepository.findCompletedLessonIdsByCourseAndUser(1812L, 83L)).thenReturn(List.of());
        when(quizAttemptRepository.findPassedQuizIdsByCourseAndUser(1812L, 83L)).thenReturn(List.of());
        when(assignmentSubmissionRepository.findPassedAssignmentIdsByCourseAndUser(1812L, 83L)).thenReturn(List.of(31L, 32L));
        when(assignmentSubmissionRepository.findPassedRequiredAssignmentIdsByCourseAndUser(1812L, 83L)).thenReturn(List.of(31L, 32L));
        when(quizAttemptRepository.findPassedQuizAttemptSummariesByCourseAndUser(1812L, 83L)).thenReturn(List.of());
        when(assignmentSubmissionRepository.findLatestPassedNewestByCourseAndUser(1812L, 83L)).thenReturn(List.of(legacySubmission));
        when(certificateService.findActiveUserCourseCertificate(1812L, 83L)).thenReturn(Optional.empty());
        when(certificateService.findUserCourseCertificate(1812L, 83L)).thenReturn(Optional.empty());

        var status = courseLearningProgressService.getCourseLearningStatus(1812L, 83L);

        assertTrue(status.getImpactedItems().stream().anyMatch(item ->
                "ASSIGNMENT".equals(item.getItemType())
                        && item.getItemId().equals(32L)
                        && Boolean.TRUE.equals(item.getIsBreakingChanged())
                        && Boolean.TRUE.equals(item.getRequiresRetake())
                        && "ITEM_NOT_IN_ACTIVE_REVISION".equals(item.getBreakingReason())
                        && item.getSourceRevisionId().equals(7061L)));
    }

    @Test
    void getCourseLearningStatus_limitsLegacyHistoryAndSetsHasMore() throws Exception {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setLearningRevisionId(707L);

        Course course = new Course();
        course.setId(1811L);
        course.setActiveRevisionId(708L);

        CourseRevision revision = CourseRevision.builder()
                .id(707L)
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "snapshotVersion": 1,
                          "modules": [
                            {
                              "id": 1,
                              "lessons": [],
                              "quizzes": [ { "id": 21 } ],
                              "assignments": []
                            }
                          ]
                        }
                        """))
                .build();

        List<QuizAttemptRepository.PassedQuizAttemptSummary> legacySummaries = new ArrayList<>();
        for (long id = 1000L; id < 1025L; id++) {
            QuizAttemptRepository.PassedQuizAttemptSummary summary = mock(QuizAttemptRepository.PassedQuizAttemptSummary.class);
            when(summary.getQuizId()).thenReturn(id);
            when(summary.getQuizTitle()).thenReturn("Legacy Quiz " + id);
            when(summary.getScore()).thenReturn(80);
            when(summary.getSubmittedAt()).thenReturn(Instant.parse("2026-01-10T10:00:00Z").minusSeconds(id));
            legacySummaries.add(summary);
        }

        when(courseRepository.findById(1811L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByCourseIdAndUserId(1811L, 82L)).thenReturn(Optional.of(enrollment));
        when(courseRevisionRepository.findByIdAndCourse_Id(707L, 1811L)).thenReturn(Optional.of(revision));
        when(lessonProgressRepository.findCompletedLessonIdsByCourseAndUser(1811L, 82L)).thenReturn(List.of());
        when(quizAttemptRepository.findPassedQuizIdsByCourseAndUser(1811L, 82L))
                .thenReturn(List.of(21L, 1000L, 1001L, 1002L));
        when(assignmentSubmissionRepository.findPassedAssignmentIdsByCourseAndUser(1811L, 82L)).thenReturn(List.of());
        when(assignmentSubmissionRepository.findPassedRequiredAssignmentIdsByCourseAndUser(1811L, 82L)).thenReturn(List.of());
        when(quizAttemptRepository.findPassedQuizAttemptSummariesByCourseAndUser(1811L, 82L))
                .thenReturn(legacySummaries);
        when(assignmentSubmissionRepository.findLatestPassedNewestByCourseAndUser(1811L, 82L)).thenReturn(List.of());
        when(certificateService.findActiveUserCourseCertificate(1811L, 82L)).thenReturn(Optional.empty());
        when(certificateService.findUserCourseCertificate(1811L, 82L)).thenReturn(Optional.empty());

        var status = courseLearningProgressService.getCourseLearningStatus(1811L, 82L);

        assertEquals(20, status.getLegacyQuizResults().size());
        assertEquals(Boolean.TRUE, status.getLegacyQuizResultsHasMore());
        assertEquals(20, status.getLegacyHistoryLimit());
    }

    @Test
    void recalculateCourseProgress_revisionScopedDoesNotQueryLegacyHistory() throws Exception {
        Course course = new Course();
        course.setId(181L);
        course.setRevisioningEnabled(true);

        User user = new User();
        user.setId(121L);

        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setCourse(course);
        enrollment.setUser(user);
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        enrollment.setLearningRevisionId(706L);

        CourseRevision revision = CourseRevision.builder()
                .id(706L)
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "snapshotVersion": 1,
                          "modules": [
                            {
                              "id": 1,
                              "lessons": [ { "id": 10 }, { "id": 11 } ],
                              "quizzes": [ { "id": 21 } ],
                              "assignments": []
                            }
                          ]
                        }
                        """))
                .build();

        when(enrollmentRepository.findByCourseIdAndUserIdForUpdate(181L, 121L))
                .thenReturn(Optional.of(enrollment));
        when(courseRepository.findById(181L)).thenReturn(Optional.of(course));
        when(courseRevisionRepository.findByIdAndCourse_Id(706L, 181L)).thenReturn(Optional.of(revision));
        when(lessonProgressRepository.findCompletedLessonIdsByCourseAndUser(181L, 121L))
                .thenReturn(List.of(10L, 11L));
        when(quizAttemptRepository.findPassedQuizIdsByCourseAndUser(181L, 121L))
                .thenReturn(List.of(21L));
        when(assignmentSubmissionRepository.findPassedAssignmentIdsByCourseAndUser(181L, 121L))
                .thenReturn(List.of());
        when(assignmentSubmissionRepository.findPassedRequiredAssignmentIdsByCourseAndUser(181L, 121L))
                .thenReturn(List.of());
        when(certificateService.findActiveUserCourseCertificate(181L, 121L)).thenReturn(Optional.empty());
        when(certificateService.findUserCourseCertificate(181L, 121L)).thenReturn(Optional.empty());

        int percent = courseLearningProgressService.recalculateCourseProgress(181L, 121L);

        assertEquals(100, percent);
        verify(quizAttemptRepository, never()).findPassedQuizAttemptSummariesByCourseAndUser(anyLong(), anyLong());
        verify(quizAttemptRepository, never()).findPassedQuizAttemptsByCourseAndUser(anyLong(), anyLong());
    }

    @Test
    void getCourseLearningStatus_breakingQuiz_marksOnlyThatQuizRequiresRetake() throws Exception {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setLearningRevisionId(8101L);

        Course course = new Course();
        course.setId(1901L);
        course.setActiveRevisionId(8102L);

        CourseRevision revision = CourseRevision.builder()
                .id(8101L)
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "snapshotVersion": 1,
                          "modules": [
                            {
                              "id": 1,
                              "lessons": [],
                              "quizzes": [ { "id": 21 } ],
                              "assignments": [ { "id": 31, "isRequired": true } ]
                            }
                          ]
                        }
                        """))
                .build();

        QuizAttemptRepository.PassedQuizAttemptSummary legacyQuiz = mock(QuizAttemptRepository.PassedQuizAttemptSummary.class);
        when(legacyQuiz.getQuizId()).thenReturn(22L);
        when(legacyQuiz.getQuizTitle()).thenReturn("Legacy Quiz");
        when(legacyQuiz.getScore()).thenReturn(88);
        when(legacyQuiz.getSubmittedAt()).thenReturn(Instant.parse("2026-01-12T10:00:00Z"));

        when(courseRepository.findById(1901L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByCourseIdAndUserId(1901L, 91L)).thenReturn(Optional.of(enrollment));
        when(courseRevisionRepository.findByIdAndCourse_Id(8101L, 1901L)).thenReturn(Optional.of(revision));
        when(lessonProgressRepository.findCompletedLessonIdsByCourseAndUser(1901L, 91L)).thenReturn(List.of());
        when(quizAttemptRepository.findPassedQuizIdsByCourseAndUser(1901L, 91L)).thenReturn(List.of(21L, 22L));
        when(assignmentSubmissionRepository.findPassedAssignmentIdsByCourseAndUser(1901L, 91L)).thenReturn(List.of(31L));
        when(assignmentSubmissionRepository.findPassedRequiredAssignmentIdsByCourseAndUser(1901L, 91L)).thenReturn(List.of(31L));
        when(quizAttemptRepository.findPassedQuizAttemptSummariesByCourseAndUser(1901L, 91L)).thenReturn(List.of(legacyQuiz));
        when(assignmentSubmissionRepository.findLatestPassedNewestByCourseAndUser(1901L, 91L)).thenReturn(List.of());
        when(certificateService.findActiveUserCourseCertificate(1901L, 91L)).thenReturn(Optional.empty());
        when(certificateService.findUserCourseCertificate(1901L, 91L)).thenReturn(Optional.empty());

        var status = courseLearningProgressService.getCourseLearningStatus(1901L, 91L);

        assertTrue(status.getImpactedItems().stream().anyMatch(item ->
                "QUIZ".equals(item.getItemType())
                        && item.getItemId().equals(22L)
                        && Boolean.TRUE.equals(item.getRequiresRetake())));
        assertFalse(status.getImpactedItems().stream().anyMatch(item ->
                "ASSIGNMENT".equals(item.getItemType()) && Boolean.TRUE.equals(item.getRequiresRetake())));
    }

    @Test
    void getCourseLearningStatus_breakingAssignment_marksOnlyThatAssignmentRequiresRetake() throws Exception {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setLearningRevisionId(8103L);

        Course course = new Course();
        course.setId(1902L);
        course.setActiveRevisionId(8104L);

        CourseRevision revision = CourseRevision.builder()
                .id(8103L)
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "snapshotVersion": 1,
                          "modules": [
                            {
                              "id": 1,
                              "lessons": [],
                              "quizzes": [ { "id": 21 } ],
                              "assignments": [ { "id": 31, "isRequired": true } ]
                            }
                          ]
                        }
                        """))
                .build();

        Assignment assignment = new Assignment();
        assignment.setId(32L);
        assignment.setTitle("Legacy Assignment");

        AssignmentSubmission legacySubmission = new AssignmentSubmission();
        legacySubmission.setAssignment(assignment);
        legacySubmission.setScore(new BigDecimal("8.50"));
        legacySubmission.setSubmittedAt(Instant.parse("2026-01-12T11:00:00Z"));

        when(courseRepository.findById(1902L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByCourseIdAndUserId(1902L, 92L)).thenReturn(Optional.of(enrollment));
        when(courseRevisionRepository.findByIdAndCourse_Id(8103L, 1902L)).thenReturn(Optional.of(revision));
        when(lessonProgressRepository.findCompletedLessonIdsByCourseAndUser(1902L, 92L)).thenReturn(List.of());
        when(quizAttemptRepository.findPassedQuizIdsByCourseAndUser(1902L, 92L)).thenReturn(List.of(21L));
        when(assignmentSubmissionRepository.findPassedAssignmentIdsByCourseAndUser(1902L, 92L)).thenReturn(List.of(31L, 32L));
        when(assignmentSubmissionRepository.findPassedRequiredAssignmentIdsByCourseAndUser(1902L, 92L)).thenReturn(List.of(31L, 32L));
        when(quizAttemptRepository.findPassedQuizAttemptSummariesByCourseAndUser(1902L, 92L)).thenReturn(List.of());
        when(assignmentSubmissionRepository.findLatestPassedNewestByCourseAndUser(1902L, 92L)).thenReturn(List.of(legacySubmission));
        when(certificateService.findActiveUserCourseCertificate(1902L, 92L)).thenReturn(Optional.empty());
        when(certificateService.findUserCourseCertificate(1902L, 92L)).thenReturn(Optional.empty());

        var status = courseLearningProgressService.getCourseLearningStatus(1902L, 92L);

        assertTrue(status.getImpactedItems().stream().anyMatch(item ->
                "ASSIGNMENT".equals(item.getItemType())
                        && item.getItemId().equals(32L)
                        && Boolean.TRUE.equals(item.getRequiresRetake())));
        assertFalse(status.getImpactedItems().stream().anyMatch(item ->
                "QUIZ".equals(item.getItemType()) && Boolean.TRUE.equals(item.getRequiresRetake())));
    }

    @Test
    void getLearningRevisionInfo_keepsUpgradeBannerDataEvenWhenStatusHasWarnings() throws Exception {
        Course course = new Course();
        course.setId(1950L);
        course.setActiveRevisionId(9202L);
        course.setLatestRevisionId(9202L);
        course.setUpgradePolicy(CourseUpgradePolicy.MANUAL);

        User user = new User();
        user.setId(95L);

        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setCourse(course);
        enrollment.setUser(user);
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        enrollment.setLearningRevisionId(9201L);

        CourseRevision pinnedRevision = CourseRevision.builder()
                .id(9201L)
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "snapshotVersion": 1,
                          "modules": [
                            {
                              "id": 1,
                              "lessons": [],
                              "quizzes": [ { "id": 21 } ],
                              "assignments": []
                            }
                          ]
                        }
                        """))
                .build();

        QuizAttemptRepository.PassedQuizAttemptSummary legacyQuiz = mock(QuizAttemptRepository.PassedQuizAttemptSummary.class);
        when(legacyQuiz.getQuizId()).thenReturn(22L);
        when(legacyQuiz.getQuizTitle()).thenReturn("Legacy Quiz");
        when(legacyQuiz.getScore()).thenReturn(86);
        when(legacyQuiz.getSubmittedAt()).thenReturn(Instant.parse("2026-01-12T12:00:00Z"));

        when(courseRepository.findById(1950L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByCourseIdAndUserId(1950L, 95L)).thenReturn(Optional.of(enrollment));
        when(courseRevisionRepository.findByIdAndCourse_Id(9201L, 1950L)).thenReturn(Optional.of(pinnedRevision));
        when(lessonProgressRepository.findCompletedLessonIdsByCourseAndUser(1950L, 95L)).thenReturn(List.of());
        when(quizAttemptRepository.findPassedQuizIdsByCourseAndUser(1950L, 95L)).thenReturn(List.of(21L, 22L));
        when(assignmentSubmissionRepository.findPassedAssignmentIdsByCourseAndUser(1950L, 95L)).thenReturn(List.of());
        when(assignmentSubmissionRepository.findPassedRequiredAssignmentIdsByCourseAndUser(1950L, 95L)).thenReturn(List.of());
        when(quizAttemptRepository.findPassedQuizAttemptSummariesByCourseAndUser(1950L, 95L)).thenReturn(List.of(legacyQuiz));
        when(assignmentSubmissionRepository.findLatestPassedNewestByCourseAndUser(1950L, 95L)).thenReturn(List.of());
        when(certificateService.findActiveUserCourseCertificate(1950L, 95L)).thenReturn(Optional.empty());
        when(certificateService.findUserCourseCertificate(1950L, 95L)).thenReturn(Optional.empty());

        var status = courseLearningProgressService.getCourseLearningStatus(1950L, 95L);
        CourseLearningRevisionInfoDTO info = courseLearningProgressService.getLearningRevisionInfo(1950L, 95L);

        assertTrue(status.getImpactedItems().stream().anyMatch(item ->
                "QUIZ".equals(item.getItemType())
                        && item.getItemId().equals(22L)
                        && Boolean.TRUE.equals(item.getRequiresRetake())));
        assertEquals(9201L, info.getLearningRevisionId());
        assertEquals(9202L, info.getActiveRevisionId());
        assertEquals(Boolean.TRUE, info.isHasNewerRevision());
        assertEquals("MANUAL", info.getUpgradePolicy());
    }

    @Test
    void upgradeToActiveRevision_updatesPinnedRevisionAndSnapshot() {
        Course course = new Course();
        course.setId(20L);
        course.setActiveRevisionId(302L);
        course.setLatestRevisionId(302L);
        course.setUpgradePolicy(CourseUpgradePolicy.MANUAL);

        User user = new User();
        user.setId(9L);

        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setCourse(course);
        enrollment.setUser(user);
        enrollment.setLearningRevisionId(301L);

        when(courseRepository.findById(20L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByCourseIdAndUserIdForUpdate(20L, 9L)).thenReturn(Optional.of(enrollment));
        when(assignmentSubmissionRepository.existsNewestPendingGradeByCourseAndUser(20L, 9L)).thenReturn(false);
        when(quizAttemptSessionRepository.existsActiveSessionByCourseAndUser(20L, 9L, QuizAttemptSessionStatus.IN_PROGRESS))
                .thenReturn(false);

        CourseLearningRevisionInfoDTO info = courseLearningProgressService.upgradeToActiveRevision(20L, 9L);

        assertEquals(302L, enrollment.getLearningRevisionId());
        assertEquals("MANUAL", enrollment.getUpgradePolicySnapshot());
        assertEquals(302L, info.getLearningRevisionId());
        assertEquals(302L, info.getActiveRevisionId());
        assertEquals(Boolean.FALSE, info.isHasNewerRevision());
        verify(enrollmentRepository).save(enrollment);
    }

    @Test
    void upgradeToActiveRevision_keepsStateWhenAlreadyOnActive() {
        Course course = new Course();
        course.setId(21L);
        course.setActiveRevisionId(402L);
        course.setLatestRevisionId(402L);
        course.setUpgradePolicy(CourseUpgradePolicy.MANUAL);

        User user = new User();
        user.setId(10L);

        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setCourse(course);
        enrollment.setUser(user);
        enrollment.setLearningRevisionId(402L);

        when(courseRepository.findById(21L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByCourseIdAndUserIdForUpdate(21L, 10L)).thenReturn(Optional.of(enrollment));

        CourseLearningRevisionInfoDTO info = courseLearningProgressService.upgradeToActiveRevision(21L, 10L);

        assertEquals(402L, info.getLearningRevisionId());
        assertEquals(402L, info.getActiveRevisionId());
        assertEquals(Boolean.FALSE, info.isHasNewerRevision());
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void upgradeToActiveRevision_throwsWhenCourseHasNoTargetRevision() {
        Course course = new Course();
        course.setId(22L);
        course.setActiveRevisionId(null);
        course.setLatestRevisionId(null);

        User user = new User();
        user.setId(11L);

        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setCourse(course);
        enrollment.setUser(user);

        when(courseRepository.findById(22L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByCourseIdAndUserIdForUpdate(22L, 11L)).thenReturn(Optional.of(enrollment));

        assertThrows(ConflictException.class, () -> courseLearningProgressService.upgradeToActiveRevision(22L, 11L));
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void upgradeToActiveRevision_throwsWhenOnlyLatestRevisionExistsButNoActiveRevision() {
        Course course = new Course();
        course.setId(221L);
        course.setActiveRevisionId(null);
        course.setLatestRevisionId(777L);

        User user = new User();
        user.setId(111L);

        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setCourse(course);
        enrollment.setUser(user);
        enrollment.setLearningRevisionId(null);

        when(courseRepository.findById(221L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByCourseIdAndUserIdForUpdate(221L, 111L)).thenReturn(Optional.of(enrollment));

        assertThrows(ConflictException.class, () -> courseLearningProgressService.upgradeToActiveRevision(221L, 111L));
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void upgradeToActiveRevision_throwsWhenPendingAssignmentGradeExists() {
        Course course = new Course();
        course.setId(23L);
        course.setActiveRevisionId(501L);
        course.setLatestRevisionId(501L);

        User user = new User();
        user.setId(12L);

        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setCourse(course);
        enrollment.setUser(user);
        enrollment.setLearningRevisionId(500L);

        when(courseRepository.findById(23L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByCourseIdAndUserIdForUpdate(23L, 12L)).thenReturn(Optional.of(enrollment));
        when(assignmentSubmissionRepository.existsNewestPendingGradeByCourseAndUser(23L, 12L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> courseLearningProgressService.upgradeToActiveRevision(23L, 12L));
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void upgradeToActiveRevision_throwsWhenQuizIsInProgress() {
        Course course = new Course();
        course.setId(24L);
        course.setActiveRevisionId(601L);
        course.setLatestRevisionId(601L);

        User user = new User();
        user.setId(13L);

        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setCourse(course);
        enrollment.setUser(user);
        enrollment.setLearningRevisionId(600L);

        when(courseRepository.findById(24L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByCourseIdAndUserIdForUpdate(24L, 13L)).thenReturn(Optional.of(enrollment));
        when(assignmentSubmissionRepository.existsNewestPendingGradeByCourseAndUser(24L, 13L)).thenReturn(false);
        when(quizAttemptSessionRepository.existsActiveSessionByCourseAndUser(24L, 13L, QuizAttemptSessionStatus.IN_PROGRESS))
                .thenReturn(true);

        assertThrows(ConflictException.class, () -> courseLearningProgressService.upgradeToActiveRevision(24L, 13L));
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void getLearningRevisionInfo_usesEffectiveRevisionWhenPinMissing() {
        Course course = new Course();
        course.setId(30L);
        course.setActiveRevisionId(901L);
        course.setLatestRevisionId(901L);
        course.setUpgradePolicy(CourseUpgradePolicy.MANUAL);

        User user = new User();
        user.setId(15L);

        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setCourse(course);
        enrollment.setUser(user);
        enrollment.setLearningRevisionId(null);

        when(courseRepository.findById(30L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByCourseIdAndUserId(30L, 15L)).thenReturn(Optional.of(enrollment));

        CourseLearningRevisionInfoDTO info = courseLearningProgressService.getLearningRevisionInfo(30L, 15L);

        assertEquals(901L, info.getLearningRevisionId());
        assertEquals(901L, info.getActiveRevisionId());
        assertFalse(info.isHasNewerRevision());
    }

    @Test
    void upgradeToActiveRevision_setsInitialPinWhenMissingWithoutUpgradeBlockingChecks() {
        Course course = new Course();
        course.setId(31L);
        course.setActiveRevisionId(1001L);
        course.setLatestRevisionId(1001L);
        course.setUpgradePolicy(CourseUpgradePolicy.MANUAL);

        User user = new User();
        user.setId(16L);

        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setCourse(course);
        enrollment.setUser(user);
        enrollment.setLearningRevisionId(null);

        when(courseRepository.findById(31L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByCourseIdAndUserIdForUpdate(31L, 16L)).thenReturn(Optional.of(enrollment));

        CourseLearningRevisionInfoDTO info = courseLearningProgressService.upgradeToActiveRevision(31L, 16L);

        assertEquals(1001L, enrollment.getLearningRevisionId());
        assertEquals("MANUAL", enrollment.getUpgradePolicySnapshot());
        assertEquals(1001L, info.getLearningRevisionId());
        assertFalse(info.isHasNewerRevision());
        verify(certificateService, never()).findActiveUserCourseCertificate(anyLong(), anyLong());
        verify(assignmentSubmissionRepository, never()).existsNewestPendingGradeByCourseAndUser(anyLong(), anyLong());
        verify(quizAttemptSessionRepository, never())
                .existsActiveSessionByCourseAndUser(anyLong(), anyLong(), eq(QuizAttemptSessionStatus.IN_PROGRESS));
        verify(enrollmentRepository).save(enrollment);
    }

        @Test
        void getLearningRevisionInfo_manualPolicy_keepsExistingPinnedRevisionUntilManualUpgrade() {
                Course course = new Course();
                course.setId(41L);
                course.setActiveRevisionId(4102L);
                course.setLatestRevisionId(4102L);
                course.setUpgradePolicy(CourseUpgradePolicy.MANUAL);

                User user = new User();
                user.setId(17L);

                CourseEnrollment enrollment = new CourseEnrollment();
                enrollment.setCourse(course);
                enrollment.setUser(user);
                enrollment.setStatus(EnrollmentStatus.ENROLLED);
                enrollment.setLearningRevisionId(4101L);

                when(courseRepository.findById(41L)).thenReturn(Optional.of(course));
                when(enrollmentRepository.findByCourseIdAndUserId(41L, 17L)).thenReturn(Optional.of(enrollment));

                CourseLearningRevisionInfoDTO info = courseLearningProgressService.getLearningRevisionInfo(41L, 17L);

                assertEquals(4101L, info.getLearningRevisionId());
                assertEquals(4102L, info.getActiveRevisionId());
                assertEquals(Boolean.TRUE, info.isHasNewerRevision());
                verify(enrollmentRepository, never()).save(any());
        }

        @Test
        void getLearningRevisionInfo_manualPolicy_doesNotAutoMoveCompletedLearner() {
                Course course = new Course();
                course.setId(42L);
                course.setActiveRevisionId(4202L);
                course.setLatestRevisionId(4202L);
                course.setUpgradePolicy(CourseUpgradePolicy.MANUAL);

                User user = new User();
                user.setId(18L);

                CourseEnrollment enrollment = new CourseEnrollment();
                enrollment.setCourse(course);
                enrollment.setUser(user);
                enrollment.setStatus(EnrollmentStatus.COMPLETED);
                enrollment.setLearningRevisionId(4201L);

                when(courseRepository.findById(42L)).thenReturn(Optional.of(course));
                when(enrollmentRepository.findByCourseIdAndUserId(42L, 18L)).thenReturn(Optional.of(enrollment));

                CourseLearningRevisionInfoDTO info = courseLearningProgressService.getLearningRevisionInfo(42L, 18L);

                assertEquals(4201L, info.getLearningRevisionId());
                assertEquals(4202L, info.getActiveRevisionId());
                assertEquals(Boolean.TRUE, info.isHasNewerRevision());
                verify(enrollmentRepository, never()).save(any());
        }
}
