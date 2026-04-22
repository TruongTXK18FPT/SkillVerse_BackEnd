package com.exe.skillverse_backend.journey_service.node_mentoring.service;

import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import com.exe.skillverse_backend.ai_service.entity.RoadmapSession;
import com.exe.skillverse_backend.ai_service.repository.RoadmapSessionRepository;
import com.exe.skillverse_backend.journey_service.entity.Journey;
import com.exe.skillverse_backend.journey_service.repository.JourneyRepository;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves and validates a (journeyId, nodeId) pair.
 * Phase 1 scope: verifies the journey exists and has a roadmap session.
 * The node identifier itself is not validated against the roadmap graph here —
 * the graph lives in ai_service JSON and a proper per-node lookup is out of scope.
 */
@Component
@RequiredArgsConstructor
public class RoadmapNodeResolver {

    private final JourneyRepository journeyRepository;
    private final RoadmapSessionRepository roadmapSessionRepo;
    private final ObjectMapper objectMapper;

    public Journey resolveJourney(Long journeyId) {
        return journeyRepository.findById(journeyId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Journey not found: " + journeyId));
    }

    /**
     * Ensures the journey is in a state where node mentoring can occur
     * (a roadmap must have been generated).
     */
    public Journey resolveJourneyWithRoadmap(Long journeyId) {
        Journey journey = resolveJourney(journeyId);
        if (journey.getRoadmapSessionId() == null) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Journey " + journeyId + " has no roadmap session yet");
        }
        return journey;
    }

    public void ensureLearnerOwns(Journey journey, Long learnerId) {
        if (journey.getUser() == null || !learnerId.equals(journey.getUser().getId())) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "You are not the owner of journey " + journey.getId());
        }
    }

    /**
     * Retrieves node content from the roadmap JSON stored in RoadmapSession.
     * Used to auto-create SYSTEM_GENERATED assignment for free learners.
     *
     * @param journey the journey containing roadmapSessionId
     * @param nodeId the node identifier to look up
     * @return RoadmapNode with content details, or null if not found
     */
    public RoadmapResponse.RoadmapNode getNodeContentFromRoadmap(Journey journey, String nodeId) {
        if (journey.getRoadmapSessionId() == null) {
            return null;
        }

        RoadmapSession session = roadmapSessionRepo.findById(journey.getRoadmapSessionId())
                .orElse(null);
        if (session == null || session.getRoadmapJson() == null) {
            return null;
        }

        try {
            RoadmapResponse roadmap = objectMapper.readValue(session.getRoadmapJson(), RoadmapResponse.class);
            if (roadmap.getRoadmap() == null) {
                return null;
            }

            return roadmap.getRoadmap().stream()
                    .filter(n -> n.getId() != null && n.getId().equals(nodeId))
                    .findFirst()
                    .orElse(null);
        } catch (JsonProcessingException e) {
            // Fail silently - allow assignment creation without rich content
            return null;
        }
    }
}
