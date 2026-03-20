package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.dto.certificatedto.CertificateDTO;
import com.exe.skillverse_backend.course_service.dto.progressdto.CourseLearningRevisionInfoDTO;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.course_service.entity.CourseRevision;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
        course.setUpgradePolicy(CourseUpgradePolicy.AUTO_COMPATIBLE_ONLY);

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
        course.setUpgradePolicy(CourseUpgradePolicy.AUTO_COMPATIBLE_ONLY);

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
        assertEquals("AUTO_COMPATIBLE_ONLY", enrollment.getUpgradePolicySnapshot());
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
        void getLearningRevisionInfo_autoCompatibleOnly_doesNotAutoMoveCompletedLearner() {
                Course course = new Course();
                course.setId(42L);
                course.setActiveRevisionId(4202L);
                course.setLatestRevisionId(4202L);
                course.setUpgradePolicy(CourseUpgradePolicy.AUTO_COMPATIBLE_ONLY);

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
