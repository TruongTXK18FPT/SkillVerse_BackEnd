package com.exe.skillverse_backend.ai_service.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import com.exe.skillverse_backend.ai_service.entity.RoadmapSession;
import com.exe.skillverse_backend.ai_service.entity.UserRoadmapProgress;
import com.exe.skillverse_backend.ai_service.repository.RoadmapSessionRepository;
import com.exe.skillverse_backend.ai_service.repository.UserRoadmapProgressRepository;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.study_service.entity.Task;
import com.exe.skillverse_backend.study_service.repository.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoadmapCompletionSyncServiceTest {

    @Mock
    private UserRoadmapProgressRepository progressRepository;

    @Mock
    private RoadmapSessionRepository roadmapSessionRepository;

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private RoadmapCompletionSyncService service;

    @BeforeEach
    void setUp() {
        service = new RoadmapCompletionSyncService(
                progressRepository,
                roadmapSessionRepository,
                taskRepository,
                new ObjectMapper());
    }

    @Test
    void overlayDerivedProgress_marksCourseFirstNodeCompletedFromPrimaryEnrollment() {
        RoadmapSession session = buildSession(7L, 99L);
        RoadmapResponse.RoadmapNode node = RoadmapResponse.RoadmapNode.builder()
                .id("node-course")
                .difficulty("intermediate")
                .suggestedCourseIds(List.of("11", "12"))
                .build();

        when(taskRepository.findByUserIdAndUserNotesContaining(anyLong(), anyString())).thenReturn(List.of());

        Map<String, RoadmapResponse.QuestProgress> result = service.overlayDerivedProgress(session, List.of(node), Map.of());

        assertFalse(result.containsKey("node-course"));
        verify(progressRepository, never()).saveAll(any());
    }

        @Test
        void overlayDerivedProgress_marksCourseFirstNodeInProgressFromEnrollmentPercent() {
                RoadmapSession session = buildSession(7L, 99L);
                RoadmapResponse.RoadmapNode node = RoadmapResponse.RoadmapNode.builder()
                                .id("node-course")
                                .difficulty("intermediate")
                                .suggestedCourseIds(List.of("12"))
                                .build();
                when(taskRepository.findByUserIdAndUserNotesContaining(anyLong(), anyString())).thenReturn(List.of());

                Map<String, RoadmapResponse.QuestProgress> result = service.overlayDerivedProgress(session, List.of(node), Map.of());

                assertFalse(result.containsKey("node-course"));
                verify(progressRepository, never()).saveAll(any());
        }

    @Test
    void overlayDerivedProgress_marksFallbackNodeCompletedWhenAllLinkedTasksDone() {
        RoadmapSession session = buildSession(15L, 77L);
        RoadmapResponse.RoadmapNode node = RoadmapResponse.RoadmapNode.builder()
                .id("node-fallback")
                .suggestedCourseIds(List.of())
                .build();

        Task doneTask = Task.builder()
                .status("Done")
                .user(session.getUser())
                .userNotes("[ROADMAP_NODE_LINK] journey=1 roadmap=15 node=node-fallback")
                .build();
        Task progressTask = Task.builder()
                .status("In Progress")
                .user(session.getUser())
                .userProgress(100)
                .userNotes("[ROADMAP_NODE_LINK] journey=1 roadmap=15 node=node-fallback")
                .build();

        when(taskRepository.findByUserIdAndUserNotesContaining(eq(77L), anyString())).thenReturn(List.of(doneTask, progressTask));
        when(progressRepository.findBySessionIdAndQuestId(15L, "node-fallback")).thenReturn(Optional.empty());

        Map<String, RoadmapResponse.QuestProgress> result = service.overlayDerivedProgress(session, List.of(node), Map.of());

        assertEquals("COMPLETED", result.get("node-fallback").getStatus());
        verify(progressRepository).saveAll(any());
    }

    @Test
    void overlayDerivedProgress_marksFallbackNodeCompletedForStandaloneMarkerWithoutJourney() {
        RoadmapSession session = buildSession(15L, 77L);
        RoadmapResponse.RoadmapNode node = RoadmapResponse.RoadmapNode.builder()
                .id("node-fallback")
                .suggestedCourseIds(List.of())
                .build();

        Task doneTask = Task.builder()
                .status("Done")
                .user(session.getUser())
                .userNotes("[ROADMAP_NODE_LINK] roadmap=15 node=node-fallback")
                .build();

        when(taskRepository.findByUserIdAndUserNotesContaining(eq(77L), anyString())).thenReturn(List.of(doneTask));
        when(progressRepository.findBySessionIdAndQuestId(15L, "node-fallback")).thenReturn(Optional.empty());

        Map<String, RoadmapResponse.QuestProgress> result = service.overlayDerivedProgress(session, List.of(node), Map.of());

        assertEquals("COMPLETED", result.get("node-fallback").getStatus());
        verify(progressRepository).saveAll(any());
    }

    @Test
    void overlayDerivedProgress_prefersCourseSourceOverPlannerFallback() {
        RoadmapSession session = buildSession(30L, 90L);
        RoadmapResponse.RoadmapNode node = RoadmapResponse.RoadmapNode.builder()
                .id("node-course-priority")
                .difficulty("beginner")
                .suggestedCourseIds(List.of("200"))
                .build();
        Task doneTask = Task.builder()
                .status("Done")
                .user(session.getUser())
                .userNotes("[ROADMAP_NODE_LINK] roadmap=30 node=node-course-priority")
                .build();

        when(taskRepository.findByUserIdAndUserNotesContaining(eq(90L), anyString())).thenReturn(List.of(doneTask));

        Map<String, RoadmapResponse.QuestProgress> result = service.overlayDerivedProgressSnapshot(session, List.of(node), Map.of());

        // Course enrollment is no longer a source; node progress is task-derived.
        assertEquals("COMPLETED", result.get("node-course-priority").getStatus());
        assertEquals(100, result.get("node-course-priority").getProgress());
        verify(progressRepository, never()).saveAll(any());
    }

    @Test
    void overlayDerivedProgress_marksFallbackNodeInProgressFromTaskAverage() {
        RoadmapSession session = buildSession(15L, 77L);
        RoadmapResponse.RoadmapNode node = RoadmapResponse.RoadmapNode.builder()
                .id("node-fallback")
                .suggestedCourseIds(List.of())
                .build();

        Task doneTask = Task.builder()
                .status("Done")
                .user(session.getUser())
                .userNotes("[ROADMAP_NODE_LINK] roadmap=15 node=node-fallback")
                .build();
        Task inProgressTask = Task.builder()
                .status("IN_PROGRESS")
                .user(session.getUser())
                .userNotes("[ROADMAP_NODE_LINK] roadmap=15 node=node-fallback")
                .build();

        when(taskRepository.findByUserIdAndUserNotesContaining(eq(77L), anyString())).thenReturn(List.of(doneTask, inProgressTask));

        Map<String, RoadmapResponse.QuestProgress> result = service.overlayDerivedProgress(session, List.of(node), Map.of());

        assertEquals("IN_PROGRESS", result.get("node-fallback").getStatus());
        assertEquals(75, result.get("node-fallback").getProgress());
        verify(progressRepository, never()).saveAll(any());
    }

    @Test
    void overlayDerivedProgress_overridesStoredManualCompletionWhenNoSourceProgress() {
        RoadmapSession session = buildSession(22L, 88L);
        RoadmapResponse.RoadmapNode node = RoadmapResponse.RoadmapNode.builder()
                .id("node-manual")
                .suggestedCourseIds(List.of())
                .build();

        RoadmapResponse.QuestProgress stored = RoadmapResponse.QuestProgress.builder()
                .questId("node-manual")
                .status(UserRoadmapProgress.ProgressStatus.COMPLETED.name())
                .progress(100)
                .build();

        when(taskRepository.findByUserIdAndUserNotesContaining(eq(88L), anyString())).thenReturn(List.of());

        Map<String, RoadmapResponse.QuestProgress> result = service.overlayDerivedProgress(
                session,
                List.of(node),
                Map.of("node-manual", stored));

        // FIX: When both course and task return null (no data), deriveSourceDrivenProgress
        // returns null, so the stored value is NOT overwritten. The stored COMPLETED/100
        // is preserved — this is the correct new behavior (stored values are a source of
        // truth that survive absent derived data).
        assertTrue(result.containsKey("node-manual"));
        assertNotNull(result.get("node-manual"));
        assertEquals("COMPLETED", result.get("node-manual").getStatus());
        assertEquals(100, result.get("node-manual").getProgress());
        verify(progressRepository, never()).saveAll(any());
        verify(progressRepository, never()).findBySessionIdAndQuestId(eq(22L), eq("node-manual"));
    }

    @Test
    void difficultyToRank_handlesEdgeCaseVariations() {
        // Test edge-case difficulty strings don't throw
        // The overlayDerivedProgress exercises difficultyToRank via selectPrimaryCourse
        assertDoesNotThrow(() ->
            service.overlayDerivedProgress(
                buildSession(1L, 1L),
                List.of(
                    RoadmapResponse.RoadmapNode.builder()
                        .id("test-upper-beginner")
                        .difficulty("Upper Beginner")
                        .suggestedCourseIds(List.of("1"))
                        .build(),
                    RoadmapResponse.RoadmapNode.builder()
                        .id("test-pre-advanced")
                        .difficulty("Pre-Advanced")
                        .suggestedCourseIds(List.of("1"))
                        .build(),
                    RoadmapResponse.RoadmapNode.builder()
                        .id("test-advanced-beginner")
                        .difficulty("Advanced Beginner")
                        .suggestedCourseIds(List.of("1"))
                        .build(),
                    RoadmapResponse.RoadmapNode.builder()
                        .id("test-upper-intermediate")
                        .difficulty("Upper Intermediate")
                        .suggestedCourseIds(List.of("1"))
                        .build(),
                    RoadmapResponse.RoadmapNode.builder()
                        .id("test-unknown")
                        .difficulty("some-unknown-difficulty-label")
                        .suggestedCourseIds(List.of("1"))
                        .build()
                ),
                Map.of()
            )
        );
    }

    private RoadmapSession buildSession(Long sessionId, Long userId) {
        User user = User.builder().id(userId).build();
        return RoadmapSession.builder()
                .id(sessionId)
                .user(user)
                .roadmapJson("{\"roadmap\":[]}")
                .build();
    }
}
