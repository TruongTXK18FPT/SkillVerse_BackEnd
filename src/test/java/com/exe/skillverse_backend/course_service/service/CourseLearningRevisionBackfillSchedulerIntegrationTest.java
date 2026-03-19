package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.course_service.policy.CourseLearningRevisionBackfillProperties;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.service.impl.CourseLearningRevisionBackfillScheduler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseLearningRevisionBackfillSchedulerIntegrationTest {

    @Mock
    private CourseEnrollmentRepository enrollmentRepository;

    @Test
    void backfillLearningRevisionId_updatesMultipleBatchesAndPublishesUpdatedMetric() {
        CourseLearningRevisionBackfillProperties properties = new CourseLearningRevisionBackfillProperties();
        properties.setEnabled(true);
        properties.setBatchSize(2);
        properties.setMaxBatchesPerRun(3);

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CourseLearningRevisionBackfillScheduler scheduler = new CourseLearningRevisionBackfillScheduler(
                enrollmentRepository,
                properties,
                meterRegistry
        );

        when(enrollmentRepository.backfillLearningRevisionBatch(eq(2)))
                .thenReturn(2)
                .thenReturn(1)
                .thenReturn(0);
        when(enrollmentRepository.countLearningRevisionBackfillRemaining()).thenReturn(5L);
        when(enrollmentRepository.countLearningRevisionBackfillNoTarget()).thenReturn(0L);

        scheduler.backfillLearningRevisionId();

        Counter updatedCounter = meterRegistry.find("course_revision_learning_revision_backfill_total")
                .tag("result", "updated")
                .tag("reason_code", "none")
                .counter();

        assertNotNull(updatedCounter);
        assertEquals(3.0, updatedCounter.count());
        verify(enrollmentRepository, times(3)).backfillLearningRevisionBatch(eq(2));
        verify(enrollmentRepository).countLearningRevisionBackfillRemaining();
        verify(enrollmentRepository).countLearningRevisionBackfillNoTarget();
    }

    @Test
    void backfillLearningRevisionId_noTargetFoundPublishesSkippedBaselineNotFoundMetric() {
        CourseLearningRevisionBackfillProperties properties = new CourseLearningRevisionBackfillProperties();
        properties.setEnabled(true);
        properties.setBatchSize(1000);
        properties.setMaxBatchesPerRun(1);

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CourseLearningRevisionBackfillScheduler scheduler = new CourseLearningRevisionBackfillScheduler(
                enrollmentRepository,
                properties,
                meterRegistry
        );

        when(enrollmentRepository.backfillLearningRevisionBatch(eq(1000))).thenReturn(0);
        when(enrollmentRepository.countLearningRevisionBackfillRemaining()).thenReturn(7L);
        when(enrollmentRepository.countLearningRevisionBackfillNoTarget()).thenReturn(7L);

        scheduler.backfillLearningRevisionId();

        Counter skippedCounter = meterRegistry.find("course_revision_learning_revision_backfill_total")
                .tag("result", "skipped")
                .tag("reason_code", "baseline_not_found")
                .counter();

        assertNotNull(skippedCounter);
        assertEquals(7.0, skippedCounter.count());
    }

    @Test
    void backfillLearningRevisionId_isIdempotentAcrossRepeatedRuns() {
        CourseLearningRevisionBackfillProperties properties = new CourseLearningRevisionBackfillProperties();
        properties.setEnabled(true);
        properties.setBatchSize(5);
        properties.setMaxBatchesPerRun(1);

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CourseLearningRevisionBackfillScheduler scheduler = new CourseLearningRevisionBackfillScheduler(
                enrollmentRepository,
                properties,
                meterRegistry
        );

        when(enrollmentRepository.backfillLearningRevisionBatch(eq(5)))
                .thenReturn(3)
                .thenReturn(0);
        when(enrollmentRepository.countLearningRevisionBackfillRemaining()).thenReturn(0L);
        when(enrollmentRepository.countLearningRevisionBackfillNoTarget()).thenReturn(0L);

        scheduler.backfillLearningRevisionId();
        scheduler.backfillLearningRevisionId();

        Counter updatedCounter = meterRegistry.find("course_revision_learning_revision_backfill_total")
                .tag("result", "updated")
                .tag("reason_code", "none")
                .counter();

        assertNotNull(updatedCounter);
        assertEquals(3.0, updatedCounter.count());
        verify(enrollmentRepository, times(2)).backfillLearningRevisionBatch(eq(5));
    }
}
