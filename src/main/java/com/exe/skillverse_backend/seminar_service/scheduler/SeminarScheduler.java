package com.exe.skillverse_backend.seminar_service.scheduler;

import com.exe.skillverse_backend.seminar_service.service.SeminarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for seminar related tasks
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SeminarScheduler {

    private final SeminarService seminarService;

    /**
     * Scheduled task to update seminar statuses (runs every 5 minutes)
     * - ACCEPTED → OPEN when startTime arrives
     * - OPEN → CLOSED when endTime passes
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void updateSeminarStatuses() {
        log.info("Updating seminar statuses...");
        try {
            // First: ACCEPTED → OPEN (seminars that have started)
            seminarService.updateStartedSeminars();

            // Then: OPEN/ACCEPTED → CLOSED (seminars that have ended)
            seminarService.updateExpiredSeminars();

            log.info("Seminar statuses update completed");
        } catch (Exception e) {
            log.error("Error updating seminar statuses: ", e);
        }
    }
}
