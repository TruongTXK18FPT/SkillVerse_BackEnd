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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseAutoCompatibleUpgradeObservabilityIntegrationTest {

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
    private Clock clock;

    @Test
    void executeAfterRevisionApproval_recordsUpgradedMetricWithPolicyAndRevisionTags() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CourseAutoCompatibleUpgradeExecutor executor = new CourseAutoCompatibleUpgradeExecutor(
                courseRepository,
                courseRevisionRepository,
                courseEnrollmentRepository,
                compatibilityChecker,
                meterRegistry,
                clock
        );

        Course course = Course.builder().id(801L).upgradePolicy(CourseUpgradePolicy.AUTO_COMPATIBLE_ONLY).build();
        CourseRevision source = CourseRevision.builder().id(810L).contentSnapshotJson(OBJECT_MAPPER.readTree("{\"v\":1}")).build();
        CourseRevision target = CourseRevision.builder().id(811L).status(CourseRevisionStatus.APPROVED).contentSnapshotJson(OBJECT_MAPPER.readTree("{\"v\":1}")).build();
        Instant now = Instant.parse("2026-03-18T04:00:00Z");

        when(courseRevisionRepository.findById(810L)).thenReturn(Optional.of(source));
        when(compatibilityChecker.evaluateCompatibility(source, target))
                .thenReturn(CourseRevisionCompatibilityChecker.CompatibilityResult.nonBreaking());
        when(clock.instant()).thenReturn(now);
        when(courseEnrollmentRepository.autoUpgradePinnedRevisionForEligibleEnrollments(
                eq(801L), eq(810L), eq(811L), eq(CourseUpgradePolicy.AUTO_COMPATIBLE_ONLY.name()), eq(now), eq(EnrollmentStatus.ENROLLED)
        )).thenReturn(4);

        executor.executeAfterRevisionApproval(course, 810L, target);

        double upgraded = meterRegistry.get("course_revision_auto_upgrade_enrollments_total")
                .tag("policy", "AUTO_COMPATIBLE_ONLY")
                .tag("course_id", "801")
                .tag("source_revision_id", "810")
                .tag("target_revision_id", "811")
                .counter()
                .count();
        assertEquals(4.0, upgraded);
    }

    @Test
    void executeAfterRevisionApproval_recordsSkippedBreakingMetricWithReasonCodeTag() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CourseAutoCompatibleUpgradeExecutor executor = new CourseAutoCompatibleUpgradeExecutor(
                courseRepository,
                courseRevisionRepository,
                courseEnrollmentRepository,
                compatibilityChecker,
                meterRegistry,
                clock
        );

        Course course = Course.builder().id(901L).upgradePolicy(CourseUpgradePolicy.AUTO_COMPATIBLE_ONLY).build();
        CourseRevision source = CourseRevision.builder().id(910L).contentSnapshotJson(OBJECT_MAPPER.readTree("{\"v\":1}")).build();
        CourseRevision target = CourseRevision.builder().id(911L).status(CourseRevisionStatus.APPROVED).contentSnapshotJson(OBJECT_MAPPER.readTree("{\"v\":2}")).build();

        when(courseRevisionRepository.findById(910L)).thenReturn(Optional.of(source));
        when(compatibilityChecker.evaluateCompatibility(source, target))
                .thenReturn(CourseRevisionCompatibilityChecker.CompatibilityResult.breaking(
                        "ITEM_RULE_CHANGED",
                        "assignment:31/passingScore"
                ));
        when(courseEnrollmentRepository.countEligibleEnrollmentsForAutoUpgrade(
                eq(901L), eq(910L), eq(CourseUpgradePolicy.AUTO_COMPATIBLE_ONLY.name()), eq(EnrollmentStatus.ENROLLED)
        )).thenReturn(3L);

        executor.executeAfterRevisionApproval(course, 910L, target);

        double skippedBreaking = meterRegistry.get("course_revision_auto_upgrade_skipped_breaking_enrollments_total")
                .tag("policy", "AUTO_COMPATIBLE_ONLY")
                .tag("reason_code", "ITEM_RULE_CHANGED")
                .tag("course_id", "901")
                .tag("source_revision_id", "910")
                .tag("target_revision_id", "911")
                .counter()
                .count();
        assertEquals(3.0, skippedBreaking);
    }

    @Test
    void executeAfterRevisionApproval_recordsExecutorErrorMetricWhenRepositoryThrows() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CourseAutoCompatibleUpgradeExecutor executor = new CourseAutoCompatibleUpgradeExecutor(
                courseRepository,
                courseRevisionRepository,
                courseEnrollmentRepository,
                compatibilityChecker,
                meterRegistry,
                clock
        );

        Course course = Course.builder().id(951L).upgradePolicy(CourseUpgradePolicy.AUTO_COMPATIBLE_ONLY).build();
        CourseRevision source = CourseRevision.builder().id(960L).contentSnapshotJson(OBJECT_MAPPER.readTree("{\"v\":1}")).build();
        CourseRevision target = CourseRevision.builder().id(961L).status(CourseRevisionStatus.APPROVED).contentSnapshotJson(OBJECT_MAPPER.readTree("{\"v\":1}")).build();
        Instant now = Instant.parse("2026-03-18T04:30:00Z");

        when(courseRevisionRepository.findById(960L)).thenReturn(Optional.of(source));
        when(compatibilityChecker.evaluateCompatibility(source, target))
                .thenReturn(CourseRevisionCompatibilityChecker.CompatibilityResult.nonBreaking());
        when(clock.instant()).thenReturn(now);
        when(courseEnrollmentRepository.autoUpgradePinnedRevisionForEligibleEnrollments(
                eq(951L), eq(960L), eq(961L), eq(CourseUpgradePolicy.AUTO_COMPATIBLE_ONLY.name()), eq(now), eq(EnrollmentStatus.ENROLLED)
        )).thenThrow(new IllegalStateException("simulated"));

        executor.executeAfterRevisionApproval(course, 960L, target);

        double errors = meterRegistry.get("course_revision_auto_upgrade_errors_total")
                .tag("policy", "AUTO_COMPATIBLE_ONLY")
                .tag("course_id", "951")
                .tag("source_revision_id", "960")
                .tag("target_revision_id", "961")
                .counter()
                .count();
        assertEquals(1.0, errors);
    }
}

