package com.exe.skillverse_backend.journey_service.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import com.exe.skillverse_backend.ai_service.entity.RoadmapSession;
import com.exe.skillverse_backend.journey_service.dto.request.StartJourneyRequest;
import com.exe.skillverse_backend.journey_service.entity.AssessmentTest;
import com.exe.skillverse_backend.journey_service.entity.Journey;
import com.exe.skillverse_backend.study_service.dto.request.GenerateScheduleRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JourneyServiceImplRoadmapNodeSchedulePolicyTest {

    private JourneyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new JourneyServiceImpl(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new ObjectMapper());
        service.studyClock = Clock.fixed(Instant.parse("2026-05-06T05:00:00Z"), ZoneOffset.UTC);
    }

    @Test
    void roadmapNodeScheduleStartsTomorrowWhenRequestedStartIsToday() throws Exception {
        GenerateScheduleRequest input = baseRequest(1);
        input.setStartDate(LocalDate.of(2026, 5, 6));

        GenerateScheduleRequest normalized = buildRoadmapNodeScheduleRequest(input);

        assertEquals(LocalDate.of(2026, 5, 7), normalized.getStartDate());
    }

    @Test
    void roadmapNodeTargetSessionsFollowDeadlineDaysAndDailyIntensity() throws Exception {
        GenerateScheduleRequest normalized = buildRoadmapNodeScheduleRequest(baseRequest(1));
        assertEquals(2, resolveTargetSessionCount(normalized));

        normalized = buildRoadmapNodeScheduleRequest(baseRequest(2));
        assertEquals(4, resolveTargetSessionCount(normalized));

        normalized = buildRoadmapNodeScheduleRequest(baseRequest(3));
        assertEquals(6, resolveTargetSessionCount(normalized));
    }

    @Test
    void challengeUpFromBeginnerPromotesToIntermediateAtEightyPercent() throws Exception {
        AssessmentTest challengeTest = AssessmentTest.builder()
                .assessmentPhase("CHALLENGE_UP")
                .baseLevel("BEGINNER")
                .testedLevel("ELEMENTARY")
                .difficultyLevel("INTERMEDIATE")
                .build();
        StartJourneyRequest assessmentData = StartJourneyRequest.builder()
                .level("BEGINNER")
                .build();

        Journey.SkillLevel evaluatedLevel = determineEvaluatedLevel(80, 10, 10, challengeTest, assessmentData);

        assertEquals(Journey.SkillLevel.INTERMEDIATE, evaluatedLevel);
    }

    @Test
    void failedChallengeUpKeepsBaseLevel() throws Exception {
        AssessmentTest challengeTest = AssessmentTest.builder()
                .assessmentPhase("CHALLENGE_UP")
                .baseLevel("BEGINNER")
                .testedLevel("INTERMEDIATE")
                .difficultyLevel("INTERMEDIATE")
                .build();
        StartJourneyRequest assessmentData = StartJourneyRequest.builder()
                .level("BEGINNER")
                .build();

        Journey.SkillLevel evaluatedLevel = determineEvaluatedLevel(69, 10, 10, challengeTest, assessmentData);

        assertEquals(Journey.SkillLevel.BEGINNER, evaluatedLevel);
    }

    private GenerateScheduleRequest baseRequest(int maxSessionsPerDay) {
        GenerateScheduleRequest request = new GenerateScheduleRequest();
        request.setTimezone("Asia/Ho_Chi_Minh");
        request.setStartDate(LocalDate.of(2026, 5, 6));
        request.setDeadline(LocalDate.of(2026, 5, 8));
        request.setDurationMinutes(60);
        request.setMaxSessionsPerDay(maxSessionsPerDay);
        return request;
    }

    private GenerateScheduleRequest buildRoadmapNodeScheduleRequest(GenerateScheduleRequest input) throws Exception {
        Method method = JourneyServiceImpl.class.getDeclaredMethod(
                "buildRoadmapNodeScheduleRequest",
                RoadmapSession.class,
                Journey.class,
                RoadmapResponse.RoadmapNode.class,
                GenerateScheduleRequest.class);
        method.setAccessible(true);
        return (GenerateScheduleRequest) method.invoke(service, null, null, roadmapNode(), input);
    }

    private int resolveTargetSessionCount(GenerateScheduleRequest request) throws Exception {
        Method method = JourneyServiceImpl.class.getDeclaredMethod(
                "resolveRoadmapNodeTargetSessionCount",
                RoadmapResponse.RoadmapNode.class,
                GenerateScheduleRequest.class);
        method.setAccessible(true);
        return (int) method.invoke(service, roadmapNode(), request);
    }

    private Journey.SkillLevel determineEvaluatedLevel(
            int scorePercentage,
            int answeredQuestions,
            int totalQuestions,
            AssessmentTest test,
            StartJourneyRequest assessmentData) throws Exception {
        Method method = JourneyServiceImpl.class.getDeclaredMethod(
                "determineEvaluatedLevel",
                int.class,
                int.class,
                int.class,
                AssessmentTest.class,
                StartJourneyRequest.class);
        method.setAccessible(true);
        return (Journey.SkillLevel) method.invoke(
                service,
                scorePercentage,
                answeredQuestions,
                totalQuestions,
                test,
                assessmentData);
    }

    private RoadmapResponse.RoadmapNode roadmapNode() {
        return RoadmapResponse.RoadmapNode.builder()
                .id("node-1")
                .title("React components")
                .estimatedTimeMinutes(120)
                .type(RoadmapResponse.RoadmapNode.NodeType.MAIN)
                .build();
    }
}
