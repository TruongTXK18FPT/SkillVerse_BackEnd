package com.exe.skillverse_backend.course_service.service.impl;

import com.exe.skillverse_backend.course_service.policy.CourseLearningRevisionBackfillProperties;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class CourseLearningRevisionBackfillScheduler {

    private static final String BACKFILL_METRIC = "course_revision_learning_revision_backfill_total";

    private final CourseEnrollmentRepository enrollmentRepository;
    private final CourseLearningRevisionBackfillProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelayString = "${app.course.revision.learning-revision-backfill.fixed-delay-ms:60000}")
    public void backfillLearningRevisionId() {
        if (!properties.isEnabled()) {
            return;
        }

        int normalizedBatchSize = Math.max(1, properties.getBatchSize());
        int maxBatches = Math.max(1, properties.getMaxBatchesPerRun());
        int totalUpdated = 0;
        int totalSkipped = 0;

        for (int i = 0; i < maxBatches; i++) {
            try {
                Integer updated = transactionTemplate.execute(status -> {
                    return enrollmentRepository.backfillLearningRevisionBatch(normalizedBatchSize);
                });
                if (updated == null || updated <= 0) {
                    break;
                }
                totalUpdated += updated;
                incrementCounter("updated", "none", updated);
            } catch (Exception e) {
                totalSkipped++;
                incrementCounter("skipped", "batch_failed", 1);
                log.warn("Backfill batch {} failed, skipping: {}", i + 1, e.getMessage());
                // Continue to next batch — don't fail entire run
            }
        }

        long remaining = enrollmentRepository.countLearningRevisionBackfillRemaining();
        long noTarget = enrollmentRepository.countLearningRevisionBackfillNoTarget();

        if (noTarget > 0) {
            incrementCounter("skipped", "baseline_not_found", noTarget);
            log.warn(
                    "course_learning_revision_backfill_event result=skipped reasonCode=baseline_not_found updated={} skipped={} remaining={} noTarget={}",
                    totalUpdated,
                    totalSkipped,
                    remaining,
                    noTarget
            );
        } else {
            log.info(
                    "course_learning_revision_backfill_event result=ok reasonCode=none updated={} skipped={} remaining={} noTarget={}",
                    totalUpdated,
                    totalSkipped,
                    remaining,
                    noTarget
            );
        }
    }

    private void incrementCounter(String result, String reasonCode, double amount) {
        if (amount <= 0 || meterRegistry == null) {
            return;
        }
        try {
            Counter counter = meterRegistry.counter(
                    BACKFILL_METRIC,
                    "result",
                    result,
                    "reason_code",
                    reasonCode
            );
            if (counter != null) {
                counter.increment(amount);
            }
        } catch (RuntimeException metricEx) {
            log.debug("Cannot publish metric {} result={} reasonCode={}", BACKFILL_METRIC, result, reasonCode, metricEx);
        }
    }
}

