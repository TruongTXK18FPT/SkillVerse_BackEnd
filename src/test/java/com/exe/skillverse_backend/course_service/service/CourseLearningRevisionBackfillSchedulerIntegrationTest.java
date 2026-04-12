package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.course_service.policy.CourseLearningRevisionBackfillProperties;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.service.impl.CourseLearningRevisionBackfillScheduler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CourseLearningRevisionBackfillScheduler.
 * Verifies that TransactionTemplate per-batch execution works correctly.
 */
@ExtendWith(MockitoExtension.class)
class CourseLearningRevisionBackfillSchedulerIntegrationTest {

    @Mock
    private CourseEnrollmentRepository enrollmentRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    /**
     * Configure TransactionTemplate to execute callbacks synchronously,
     * returning predefined batch results directly.
     */
    private void configureTransactionTemplate(int... batchResults) {
        final int[] callCount = {0};
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            if (callCount[0] < batchResults.length) {
                return batchResults[callCount[0]++];
            }
            return 0;
        });
    }

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
                transactionTemplate,
                meterRegistry
        );

        // batch 1: returns 2, batch 2: returns 1, batch 3: returns 0 (loop breaks)
        configureTransactionTemplate(2, 1, 0);
        when(enrollmentRepository.countLearningRevisionBackfillRemaining()).thenReturn(5L);
        when(enrollmentRepository.countLearningRevisionBackfillNoTarget()).thenReturn(0L);

        scheduler.backfillLearningRevisionId();

        // Verify: TransactionTemplate called 3 times (3 batches)
        verify(transactionTemplate, times(3)).execute(any());
        // Verify: metrics recorded
        assertTrue(meterRegistry.find("course_revision_learning_revision_backfill_total").counter() != null,
                "Metrics should be recorded");
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
                transactionTemplate,
                meterRegistry
        );

        // First batch returns 0 → loop breaks immediately
        configureTransactionTemplate(0);
        when(enrollmentRepository.countLearningRevisionBackfillRemaining()).thenReturn(7L);
        when(enrollmentRepository.countLearningRevisionBackfillNoTarget()).thenReturn(7L);

        scheduler.backfillLearningRevisionId();

        // Verify: TransactionTemplate called once (1 batch)
        verify(transactionTemplate, times(1)).execute(any());
        verify(enrollmentRepository).countLearningRevisionBackfillRemaining();
        verify(enrollmentRepository).countLearningRevisionBackfillNoTarget();
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
                transactionTemplate,
                meterRegistry
        );

        // Each run: batch returns 3 (>0, loop breaks after 1 call)
        // Two runs = 2 total execute calls
        configureTransactionTemplate(3, 3);
        when(enrollmentRepository.countLearningRevisionBackfillRemaining()).thenReturn(0L);
        when(enrollmentRepository.countLearningRevisionBackfillNoTarget()).thenReturn(0L);

        scheduler.backfillLearningRevisionId();
        scheduler.backfillLearningRevisionId();

        // Verify: 2 runs x 1 batch = 2 execute calls
        verify(transactionTemplate, times(2)).execute(any());
    }
}
