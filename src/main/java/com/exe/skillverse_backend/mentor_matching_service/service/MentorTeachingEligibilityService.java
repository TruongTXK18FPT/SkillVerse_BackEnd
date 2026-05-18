package com.exe.skillverse_backend.mentor_matching_service.service;

import com.exe.skillverse_backend.mentor_matching_service.dto.MentorTeachingEligibilityResponse;

public interface MentorTeachingEligibilityService {
    MentorTeachingEligibilityResponse evaluateRoadmap(Long mentorId, Long roadmapSessionId, String nodeId);

    MentorTeachingEligibilityResponse evaluateJourney(Long mentorId, Long journeyId, String nodeId);

    void assertCanTeachBooking(Long mentorId, Long journeyId, String nodeId, String bookingType);
}
