package com.exe.skillverse_backend.study_service.controller;

import com.exe.skillverse_backend.study_service.dto.request.CreateStudySessionRequest;
import com.exe.skillverse_backend.study_service.dto.request.GenerateScheduleRequest;
import com.exe.skillverse_backend.study_service.dto.response.StudySessionResponse;
import com.exe.skillverse_backend.study_service.entity.StudySessionStatus;
import com.exe.skillverse_backend.study_service.service.AiStudySupportService;
import com.exe.skillverse_backend.study_service.service.StudyPlannerService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import com.exe.skillverse_backend.study_service.dto.request.CheckScheduleHealthRequest;
import com.exe.skillverse_backend.study_service.dto.request.RefineScheduleRequest;
import com.exe.skillverse_backend.study_service.dto.response.ScheduleHealthReport;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/study-planner")
@RequiredArgsConstructor
public class StudyPlannerController {

    private final StudyPlannerService studyPlannerService;
    private final AiStudySupportService aiStudySupportService;

    private Long getUserId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }

    @PostMapping("/sessions")
    public ResponseEntity<StudySessionResponse> createSession(@RequestBody CreateStudySessionRequest request, Authentication authentication) {
        return ResponseEntity.ok(studyPlannerService.createSession(getUserId(authentication), request));
    }

    @PostMapping("/sessions/batch")
    public ResponseEntity<List<StudySessionResponse>> createSessions(@RequestBody List<CreateStudySessionRequest> requests, Authentication authentication) {
        return ResponseEntity.ok(studyPlannerService.createSessions(getUserId(authentication), requests));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<StudySessionResponse>> getSessions(Authentication authentication) {
        return ResponseEntity.ok(studyPlannerService.getSessions(getUserId(authentication)));
    }

    @GetMapping("/sessions/range")
    public ResponseEntity<List<StudySessionResponse>> getSessionsInRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            Authentication authentication) {
        return ResponseEntity.ok(studyPlannerService.getSessionsInRange(getUserId(authentication), start, end));
    }

    @PatchMapping("/sessions/{sessionId}/status")
    public ResponseEntity<StudySessionResponse> updateStatus(
            @PathVariable UUID sessionId,
            @RequestParam StudySessionStatus status) {
        return ResponseEntity.ok(studyPlannerService.updateStatus(sessionId, status));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable UUID sessionId) {
        studyPlannerService.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/generate-schedule")
    public ResponseEntity<List<StudySessionResponse>> generateSchedule(@RequestBody GenerateScheduleRequest request, Authentication authentication) {
        return ResponseEntity.ok(aiStudySupportService.generateSchedule(getUserId(authentication), request));
    }

    @PostMapping("/generate-proposal")
    public ResponseEntity<List<StudySessionResponse>> generateProposal(@RequestBody GenerateScheduleRequest request, Authentication authentication) {
        return ResponseEntity.ok(aiStudySupportService.generateProposedSchedule(getUserId(authentication), request));
    }

    @PostMapping("/refine-schedule")
    public ResponseEntity<List<StudySessionResponse>> refineSchedule(@RequestBody RefineScheduleRequest request, Authentication authentication) {
        return ResponseEntity.ok(aiStudySupportService.refineSchedule(getUserId(authentication), request));
    }

    @PostMapping("/schedule-health")
    public ResponseEntity<ScheduleHealthReport> checkHealth(
            @RequestBody CheckScheduleHealthRequest request) {
        return ResponseEntity.ok(aiStudySupportService.checkScheduleHealth(request));
    }

    @PostMapping("/schedule-suggest-fix")
    public ResponseEntity<ScheduleHealthReport> suggestFix(
            @RequestBody CheckScheduleHealthRequest request) {
        return ResponseEntity.ok(aiStudySupportService.suggestHealthyAdjustments(request));
    }
}
