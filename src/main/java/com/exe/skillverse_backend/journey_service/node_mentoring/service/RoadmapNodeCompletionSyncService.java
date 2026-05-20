package com.exe.skillverse_backend.journey_service.node_mentoring.service;

import com.exe.skillverse_backend.journey_service.entity.Journey;

public interface RoadmapNodeCompletionSyncService {
    void syncNodeCompletionState(Journey journey, String nodeId, boolean completed);
    void recalculateAndSyncJourneyProgress(Journey journey);
}
