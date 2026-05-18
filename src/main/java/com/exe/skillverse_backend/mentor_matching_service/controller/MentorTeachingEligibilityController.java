package com.exe.skillverse_backend.mentor_matching_service.controller;

import com.exe.skillverse_backend.mentor_matching_service.dto.MentorTeachingEligibilityResponse;
import com.exe.skillverse_backend.mentor_matching_service.service.MentorTeachingEligibilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mentor-eligibility")
@RequiredArgsConstructor
public class MentorTeachingEligibilityController {

    private final MentorTeachingEligibilityService eligibilityService;

    @GetMapping("/roadmaps/{roadmapSessionId}/mentors/{mentorId}")
    public MentorTeachingEligibilityResponse evaluateRoadmap(
            @PathVariable Long roadmapSessionId,
            @PathVariable Long mentorId,
            @RequestParam(required = false) String nodeId) {
        return eligibilityService.evaluateRoadmap(mentorId, roadmapSessionId, nodeId);
    }

    @GetMapping("/journeys/{journeyId}/mentors/{mentorId}")
    public MentorTeachingEligibilityResponse evaluateJourney(
            @PathVariable Long journeyId,
            @PathVariable Long mentorId,
            @RequestParam(required = false) String nodeId) {
        return eligibilityService.evaluateJourney(mentorId, journeyId, nodeId);
    }
}
