package com.exe.skillverse_backend.business_service.service.impl;

import com.exe.skillverse_backend.business_service.service.JobBoostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for job boost operations
 * - Auto-expire boosts when time runs out
 * - Auto-activate scheduled boosts
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JobBoostSchedulerImpl {

    private final JobBoostService jobBoostService;

    /**
     * Process expired boosts every 15 minutes
     */
    @Scheduled(fixedRate = 900000) // 15 minutes
    public void processExpiredBoosts() {
        log.debug("Running scheduled job: processExpiredBoosts");
        try {
            jobBoostService.processExpiredBoosts();
        } catch (Exception e) {
            log.error("Error processing expired boosts: {}", e.getMessage(), e);
        }
    }

    /**
     * Activate scheduled boosts every 5 minutes
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void activateScheduledBoosts() {
        log.debug("Running scheduled job: activateScheduledBoosts");
        try {
            jobBoostService.activateScheduledBoosts();
        } catch (Exception e) {
            log.error("Error activating scheduled boosts: {}", e.getMessage(), e);
        }
    }
}
