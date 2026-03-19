package com.exe.skillverse_backend.gamification_service.service.impl;

import com.exe.skillverse_backend.gamification_service.dto.request.LogActivityRequest;
import com.exe.skillverse_backend.gamification_service.entity.GamificationActivityLog;
import com.exe.skillverse_backend.gamification_service.repository.GamificationActivityLogRepository;
import com.exe.skillverse_backend.gamification_service.service.GamificationActivityService;
import com.exe.skillverse_backend.gamification_service.service.GamificationWalletService;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of GamificationActivityService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GamificationActivityServiceImpl implements GamificationActivityService {

    private final GamificationActivityLogRepository activityRepository;
    private final GamificationWalletService walletService;

    @Override
    @Transactional
    public void logActivity(Long userId, LogActivityRequest request) {
        log.info("Logging activity for user {}: {} - {}", userId, request.getActivityType(), request.getActivityAction());

        GamificationActivityLog activityLog = GamificationActivityLog.builder()
                .userId(userId)
                .activityType(request.getActivityType())
                .activityAction(request.getActivityAction())
                .targetType(request.getTargetType())
                .targetId(request.getTargetId())
                .activityData(request.getActivityData())
                .durationMinutes(request.getDurationMinutes())
                .isVerified(true) // Auto-verify for now, or implement verification logic
                .activityTimestamp(LocalDateTime.now())
                .build();

        activityRepository.save(activityLog);
        
        // Auto-process rewards for simple activities
        // More complex reward logic could be moved to a separate processor
        if ("CONTRIBUTION".equals(request.getActivityType()) && "CREATE_POST".equals(request.getActivityAction())) {
            // Reward for creating a post
            walletService.awardCoins(userId, 5, 10, "CONTRIBUTION", activityLog.getActivityLogId(), "Created a community post");
            walletService.updateUserStreak(userId);
        } else if ("STUDY".equals(request.getActivityType()) && "COMPLETE_LESSON".equals(request.getActivityAction())) {
            // Reward for completing a lesson
            walletService.awardCoins(userId, 10, 20, "STUDY", activityLog.getActivityLogId(), "Completed a lesson");
            walletService.updateUserStreak(userId);
        }
    }

    @Override
    @Transactional
    public void verifyAndProcessActivity(Long activityLogId) {
        GamificationActivityLog activity = activityRepository.findById(activityLogId)
                .orElseThrow(() -> new RuntimeException("Activity not found"));
        
        if (activity.getIsVerified()) {
            return;
        }

        // Verification logic here
        activity.setIsVerified(true);
        activityRepository.save(activity);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getUserActivitySummary(Long userId, LocalDateTime since) {
        Map<String, Object> summary = new HashMap<>();
        
        Long contributions = countActivitiesByType(userId, "CONTRIBUTION", since);
        Long studySessions = countActivitiesByType(userId, "STUDY", since);
        Integer studyMinutes = getTotalStudyMinutes(userId, since);
        
        summary.put("contributions", contributions);
        summary.put("studySessions", studySessions);
        summary.put("studyMinutes", studyMinutes);
        
        return summary;
    }

    @Override
    @Transactional(readOnly = true)
    public Integer getTotalStudyMinutes(Long userId, LocalDateTime since) {
        Integer minutes = activityRepository.sumStudyMinutesSince(userId, since);
        return minutes != null ? minutes : 0;
    }

    @Override
    @Transactional(readOnly = true)
    public Long countActivitiesByType(Long userId, String activityType, LocalDateTime since) {
        return activityRepository.countVerifiedActivitiesByTypeSince(userId, activityType, since);
    }
}
