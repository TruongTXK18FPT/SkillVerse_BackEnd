package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.CourseRevision;
import com.exe.skillverse_backend.course_service.entity.enums.CourseRevisionStatus;
import com.exe.skillverse_backend.course_service.entity.enums.CourseUpgradePolicy;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.policy.CourseRevisionCompatibilityChecker;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRevisionRepository;
import com.exe.skillverse_backend.course_service.service.impl.CourseAutoCompatibleUpgradeExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseAutoCompatibleUpgradeExecutorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private CourseRevisionRepository courseRevisionRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseEnrollmentRepository courseEnrollmentRepository;

    @Mock
    private CourseRevisionCompatibilityChecker compatibilityChecker;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Clock clock;

    @InjectMocks
    private CourseAutoCompatibleUpgradeExecutor executor;

    @Test
    void executeAfterRevisionApproval_skipsWhenPolicyIsManual() {
        Course course = Course.builder()
                .id(40L)
                .upgradePolicy(CourseUpgradePolicy.MANUAL)
                .build();
        CourseRevision approved = CourseRevision.builder()
                .id(502L)
                .status(CourseRevisionStatus.APPROVED)
                .build();

        CourseAutoCompatibleUpgradeExecutor.AutoUpgradeExecutionResult upgradedResult =
                executor.executeAfterRevisionApproval(course, 501L, approved);

        assertEquals(0, upgradedResult.getUpgradedCount());
        assertEquals("SKIPPED", upgradedResult.getOutcome());
        verify(courseRevisionRepository, never()).findById(anyLong());
        verify(courseEnrollmentRepository, never()).countEligibleEnrollmentsForAutoUpgrade(
                anyLong(), anyLong(), any(), any()
        );
        verify(courseEnrollmentRepository, never()).autoUpgradePinnedRevisionForEligibleEnrollments(
                anyLong(), anyLong(), anyLong(), any(), any(), any()
        );
    }

    @Test
    void executeAfterRevisionApproval_autoCompatibleAndNonBreaking_upgradesEligibleEnrollments() throws Exception {
        long courseId = 50L;
        long sourceRevisionId = 601L;
        long approvedRevisionId = 602L;
        Instant now = Instant.parse("2026-03-16T03:00:00Z");

        Course course = Course.builder()
                .id(courseId)
                .upgradePolicy(CourseUpgradePolicy.AUTO_COMPATIBLE_ONLY)
                .build();

        CourseRevision sourceRevision = CourseRevision.builder()
                .id(sourceRevisionId)
                .contentSnapshotJson(OBJECT_MAPPER.readTree("{\"moduleCount\":2}"))
                .build();

        CourseRevision approvedRevision = CourseRevision.builder()
                .id(approvedRevisionId)
                .status(CourseRevisionStatus.APPROVED)
                .contentSnapshotJson(OBJECT_MAPPER.readTree("{\"moduleCount\":2}"))
                .build();

        when(courseRevisionRepository.findById(sourceRevisionId)).thenReturn(Optional.of(sourceRevision));
        when(compatibilityChecker.evaluateCompatibility(sourceRevision, approvedRevision))
                .thenReturn(CourseRevisionCompatibilityChecker.CompatibilityResult.nonBreaking());
        when(clock.instant()).thenReturn(now);
        when(courseEnrollmentRepository.autoUpgradePinnedRevisionForEligibleEnrollments(
                eq(courseId),
                eq(sourceRevisionId),
                eq(approvedRevisionId),
                eq(CourseUpgradePolicy.AUTO_COMPATIBLE_ONLY.name()),
                eq(now),
                eq(EnrollmentStatus.ENROLLED)))
                .thenReturn(8);

        CourseAutoCompatibleUpgradeExecutor.AutoUpgradeExecutionResult upgradedResult =
                executor.executeAfterRevisionApproval(course, sourceRevisionId, approvedRevision);

        assertEquals(8, upgradedResult.getUpgradedCount());
        assertEquals("UPGRADED", upgradedResult.getOutcome());
        verify(courseEnrollmentRepository).autoUpgradePinnedRevisionForEligibleEnrollments(
                eq(courseId),
                eq(sourceRevisionId),
                eq(approvedRevisionId),
                eq(CourseUpgradePolicy.AUTO_COMPATIBLE_ONLY.name()),
                eq(now),
                eq(EnrollmentStatus.ENROLLED)
        );
    }

    @Test
    void executeAfterRevisionApproval_autoCompatibleButBreaking_skipsAutoUpgrade() throws Exception {
        Course course = Course.builder()
                .id(60L)
                .upgradePolicy(CourseUpgradePolicy.AUTO_COMPATIBLE_ONLY)
                .build();

        CourseRevision sourceRevision = CourseRevision.builder()
                .id(701L)
                .contentSnapshotJson(OBJECT_MAPPER.readTree("{\"v\":1}"))
                .build();

        CourseRevision approvedRevision = CourseRevision.builder()
                .id(702L)
                .status(CourseRevisionStatus.APPROVED)
                .contentSnapshotJson(OBJECT_MAPPER.readTree("{\"v\":2}"))
                .build();

        when(courseRevisionRepository.findById(701L)).thenReturn(Optional.of(sourceRevision));
        when(compatibilityChecker.evaluateCompatibility(sourceRevision, approvedRevision))
                .thenReturn(CourseRevisionCompatibilityChecker.CompatibilityResult.breaking(
                        "ITEM_RULE_CHANGED",
                        "quiz:21/passScore"
                ));
        when(courseEnrollmentRepository.countEligibleEnrollmentsForAutoUpgrade(
                eq(60L),
                eq(701L),
                eq(CourseUpgradePolicy.AUTO_COMPATIBLE_ONLY.name()),
                eq(EnrollmentStatus.ENROLLED)
        )).thenReturn(5L);

        CourseAutoCompatibleUpgradeExecutor.AutoUpgradeExecutionResult upgradedResult =
                executor.executeAfterRevisionApproval(course, 701L, approvedRevision);

        assertEquals(0, upgradedResult.getUpgradedCount());
        assertEquals("SKIPPED", upgradedResult.getOutcome());
        assertEquals("ITEM_RULE_CHANGED", upgradedResult.getReasonCode());
        verify(courseEnrollmentRepository).countEligibleEnrollmentsForAutoUpgrade(
                eq(60L),
                eq(701L),
                eq(CourseUpgradePolicy.AUTO_COMPATIBLE_ONLY.name()),
                eq(EnrollmentStatus.ENROLLED)
        );
        verify(courseEnrollmentRepository, never()).autoUpgradePinnedRevisionForEligibleEnrollments(
                anyLong(), anyLong(), anyLong(), any(), any(), any()
        );
    }

    @Test
    void executePostCommitReconciliation_autoCompatibleAndNonBreaking_upgradesEligibleEnrollments() throws Exception {
        long courseId = 80L;
        long sourceRevisionId = 901L;
        long targetRevisionId = 902L;
        Instant now = Instant.parse("2026-03-17T03:00:00Z");

        Course course = Course.builder()
                .id(courseId)
                .upgradePolicy(CourseUpgradePolicy.AUTO_COMPATIBLE_ONLY)
                .build();

        CourseRevision sourceRevision = CourseRevision.builder()
                .id(sourceRevisionId)
                .contentSnapshotJson(OBJECT_MAPPER.readTree("{\"moduleCount\":2}"))
                .build();

        CourseRevision targetRevision = CourseRevision.builder()
                .id(targetRevisionId)
                .status(CourseRevisionStatus.APPROVED)
                .contentSnapshotJson(OBJECT_MAPPER.readTree("{\"moduleCount\":2}"))
                .build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseRevisionRepository.findById(sourceRevisionId)).thenReturn(Optional.of(sourceRevision));
        when(courseRevisionRepository.findById(targetRevisionId)).thenReturn(Optional.of(targetRevision));
        when(compatibilityChecker.evaluateCompatibility(sourceRevision, targetRevision))
                .thenReturn(CourseRevisionCompatibilityChecker.CompatibilityResult.nonBreaking());
        when(clock.instant()).thenReturn(now);
        when(courseEnrollmentRepository.autoUpgradePinnedRevisionForEligibleEnrollments(
                eq(courseId),
                eq(sourceRevisionId),
                eq(targetRevisionId),
                eq(CourseUpgradePolicy.AUTO_COMPATIBLE_ONLY.name()),
                eq(now),
                eq(EnrollmentStatus.ENROLLED)))
                .thenReturn(3);

        CourseAutoCompatibleUpgradeExecutor.AutoUpgradeExecutionResult upgradedResult =
                executor.executePostCommitReconciliation(courseId, sourceRevisionId, targetRevisionId);

        assertEquals(3, upgradedResult.getUpgradedCount());
        assertEquals("UPGRADED", upgradedResult.getOutcome());
        verify(courseRepository).findById(courseId);
        verify(courseRevisionRepository).findById(targetRevisionId);
        verify(courseEnrollmentRepository).autoUpgradePinnedRevisionForEligibleEnrollments(
                eq(courseId),
                eq(sourceRevisionId),
                eq(targetRevisionId),
                eq(CourseUpgradePolicy.AUTO_COMPATIBLE_ONLY.name()),
                eq(now),
                eq(EnrollmentStatus.ENROLLED)
        );
    }

    @Test
    void executePostCommitReconciliation_returnsZeroWhenInputInvalid() {
        CourseAutoCompatibleUpgradeExecutor.AutoUpgradeExecutionResult upgradedResult =
                executor.executePostCommitReconciliation(null, 1L, 2L);

        assertEquals(0, upgradedResult.getUpgradedCount());
        assertEquals("SKIPPED", upgradedResult.getOutcome());
        verify(courseRepository, never()).findById(anyLong());
    }

    @Test
    void executeAfterRevisionApproval_skipsWhenTargetRevisionIsNotApproved() {
        Course course = Course.builder()
                .id(91L)
                .upgradePolicy(CourseUpgradePolicy.AUTO_COMPATIBLE_ONLY)
                .build();
        CourseRevision target = CourseRevision.builder()
                .id(992L)
                .status(CourseRevisionStatus.PENDING)
                .build();

        CourseAutoCompatibleUpgradeExecutor.AutoUpgradeExecutionResult result =
                executor.executeAfterRevisionApproval(course, 991L, target);

        assertEquals("SKIPPED", result.getOutcome());
        assertEquals("TARGET_REVISION_NOT_APPROVED", result.getReasonCode());
        verify(courseRevisionRepository, never()).findById(anyLong());
        verify(courseEnrollmentRepository, never()).autoUpgradePinnedRevisionForEligibleEnrollments(
                anyLong(), anyLong(), anyLong(), any(), any(), any()
        );
    }

    @Test
    void executeAfterRevisionApproval_returnsInvalidInputWhenSourceRevisionIdIsNull() {
        Course course = Course.builder()
                .id(92L)
                .upgradePolicy(CourseUpgradePolicy.AUTO_COMPATIBLE_ONLY)
                .build();
        CourseRevision target = CourseRevision.builder()
                .id(993L)
                .status(CourseRevisionStatus.APPROVED)
                .contentSnapshotJson(OBJECT_MAPPER.valueToTree(java.util.Map.of(
                        "compatibility", java.util.Map.of("autoCompatibleOnly", true)
                )))
                .build();

        CourseAutoCompatibleUpgradeExecutor.AutoUpgradeExecutionResult result =
                executor.executeAfterRevisionApproval(course, null, target);

        assertEquals("SKIPPED", result.getOutcome());
        assertEquals("INVALID_INPUT", result.getReasonCode());
    }

    @Test
    void executeAfterRevisionApproval_concurrentExecutors_doNotDuplicateUpgradeAndKeepRevisionConsistent() throws Exception {
        long courseId = 120L;
        long sourceRevisionId = 8001L;
        long targetRevisionId = 8002L;
        Instant now = Instant.parse("2026-03-18T02:00:00Z");

        Course course = Course.builder()
                .id(courseId)
                .upgradePolicy(CourseUpgradePolicy.AUTO_COMPATIBLE_ONLY)
                .build();
        CourseRevision sourceRevision = CourseRevision.builder().id(sourceRevisionId).contentSnapshotJson(OBJECT_MAPPER.readTree("{\"v\":1}")).build();
        CourseRevision targetRevision = CourseRevision.builder().id(targetRevisionId).status(CourseRevisionStatus.APPROVED).contentSnapshotJson(OBJECT_MAPPER.readTree("{\"v\":1}")).build();

        when(courseRevisionRepository.findById(sourceRevisionId)).thenReturn(Optional.of(sourceRevision));
        when(compatibilityChecker.evaluateCompatibility(sourceRevision, targetRevision))
                .thenReturn(CourseRevisionCompatibilityChecker.CompatibilityResult.nonBreaking());
        when(clock.instant()).thenReturn(now);

        AtomicBoolean upgradedOnce = new AtomicBoolean(false);
        AtomicLong learningRevisionId = new AtomicLong(sourceRevisionId);
        when(courseEnrollmentRepository.autoUpgradePinnedRevisionForEligibleEnrollments(
                eq(courseId),
                eq(sourceRevisionId),
                eq(targetRevisionId),
                eq(CourseUpgradePolicy.AUTO_COMPATIBLE_ONLY.name()),
                eq(now),
                eq(EnrollmentStatus.ENROLLED)))
                .thenAnswer(invocation -> {
                    if (upgradedOnce.compareAndSet(false, true)) {
                        learningRevisionId.set(targetRevisionId);
                        return 1;
                    }
                    return 0;
                });

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<CourseAutoCompatibleUpgradeExecutor.AutoUpgradeExecutionResult>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    startLatch.await(3, TimeUnit.SECONDS);
                    return executor.executeAfterRevisionApproval(course, sourceRevisionId, targetRevision);
                }));
            }
            startLatch.countDown();

            int totalUpgraded = 0;
            for (Future<CourseAutoCompatibleUpgradeExecutor.AutoUpgradeExecutionResult> future : futures) {
                totalUpgraded += future.get(5, TimeUnit.SECONDS).getUpgradedCount();
            }

            assertEquals(1, totalUpgraded);
            assertEquals(targetRevisionId, learningRevisionId.get());
            assertTrue(upgradedOnce.get());
        } finally {
            pool.shutdownNow();
        }
    }
}
