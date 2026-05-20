package com.exe.skillverse_backend.journey_service.node_mentoring.service.impl;

import com.exe.skillverse_backend.ai_service.entity.RoadmapSession;
import com.exe.skillverse_backend.ai_service.entity.UserRoadmapProgress;
import com.exe.skillverse_backend.ai_service.repository.RoadmapSessionRepository;
import com.exe.skillverse_backend.ai_service.repository.UserRoadmapProgressRepository;
import com.exe.skillverse_backend.journey_service.entity.Journey;
import com.exe.skillverse_backend.journey_service.repository.JourneyRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.service.RoadmapNodeCompletionSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoadmapNodeCompletionSyncServiceImpl implements RoadmapNodeCompletionSyncService {

    private final JourneyRepository journeyRepository;
    private final RoadmapSessionRepository roadmapSessionRepository;
    private final UserRoadmapProgressRepository progressRepository;

    @Transactional
    @Override
    public void syncNodeCompletionState(Journey journey, String nodeId, boolean completed) {
        if (journey == null || journey.getRoadmapSessionId() == null || nodeId == null || nodeId.isBlank()) {
            return;
        }

        RoadmapSession roadmapSession = roadmapSessionRepository
                .findById(journey.getRoadmapSessionId())
                .orElse(null);
        if (roadmapSession == null) {
            return;
        }

        UserRoadmapProgress progress = progressRepository
                .findBySessionIdAndQuestId(roadmapSession.getId(), nodeId)
                .orElse(UserRoadmapProgress.builder()
                        .roadmapSession(roadmapSession)
                        .questId(nodeId)
                        .status(UserRoadmapProgress.ProgressStatus.NOT_STARTED)
                        .progress(0)
                        .build());

        if (completed) {
            progress.setStatus(UserRoadmapProgress.ProgressStatus.COMPLETED);
            progress.setProgress(100);
            if (progress.getCompletedAt() == null) {
                progress.setCompletedAt(Instant.now());
            }
        } else {
            progress.setStatus(UserRoadmapProgress.ProgressStatus.NOT_STARTED);
            progress.setProgress(0);
            progress.setCompletedAt(null);
        }

        progressRepository.save(progress);
    }

    @Transactional
    @Override
    public void recalculateAndSyncJourneyProgress(Journey journey) {
        if (journey == null || journey.getRoadmapSessionId() == null) {
            return;
        }
        RoadmapSession session = roadmapSessionRepository
                .findById(journey.getRoadmapSessionId())
                .orElse(null);
        if (session == null) {
            return;
        }
        List<UserRoadmapProgress> allProgress =
                progressRepository.findBySessionId(journey.getRoadmapSessionId());
        long completedCount = allProgress.stream()
                .filter(p -> p.getStatus() == UserRoadmapProgress.ProgressStatus.COMPLETED)
                .count();
        int totalNodes = (session.getTotalNodes() != null && session.getTotalNodes() > 0)
                ? session.getTotalNodes()
                : allProgress.size();
        if (totalNodes == 0) {
            return;
        }
        int roadmapPct = (int) Math.round(completedCount * 100.0 / totalNodes);
        int mapped = Math.min(90, Math.max(30, (int) Math.round(30 + roadmapPct * 0.6)));
        int current = journey.getProgressPercentage() != null ? journey.getProgressPercentage() : 0;
        int next = mapped;
        if (!Objects.equals(journey.getProgressPercentage(), next)) {
            journey.setProgressPercentage(next);
            journeyRepository.save(journey);
        }
    }
}
