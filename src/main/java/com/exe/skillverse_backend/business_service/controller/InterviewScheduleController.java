package com.exe.skillverse_backend.business_service.controller;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.dto.request.CreateInterviewRequest;
import com.exe.skillverse_backend.business_service.dto.request.DeclineInterviewRequest;
import com.exe.skillverse_backend.business_service.dto.response.InterviewScheduleResponse;
import com.exe.skillverse_backend.business_service.service.InterviewScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
@Slf4j
public class InterviewScheduleController {

    private final InterviewScheduleService interviewScheduleService;
    private final UserRepository userRepository;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InterviewScheduleResponse> scheduleInterview(
            @Valid @RequestBody CreateInterviewRequest request) {
        Long userId = getCurrentUserId();
        InterviewScheduleResponse response = interviewScheduleService.scheduleInterview(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/application/{applicationId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InterviewScheduleResponse> getInterviewByApplication(
            @PathVariable Long applicationId) {
        InterviewScheduleResponse response = interviewScheduleService.getInterviewByApplicationId(applicationId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/job/{jobPostingId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<InterviewScheduleResponse>> getInterviewsByJob(
            @PathVariable Long jobPostingId) {
        List<InterviewScheduleResponse> responses = interviewScheduleService.getInterviewsByJobPostingId(jobPostingId);
        return ResponseEntity.ok(responses);
    }

    @PatchMapping("/{interviewId}/confirm")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InterviewScheduleResponse> confirmInterview(@PathVariable Long interviewId) {
        Long userId = getCurrentUserId();
        InterviewScheduleResponse response = interviewScheduleService.confirmInterview(userId, interviewId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{interviewId}/decline")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InterviewScheduleResponse> declineInterview(
            @PathVariable Long interviewId,
            @RequestBody(required = false) DeclineInterviewRequest request) {
        Long userId = getCurrentUserId();
        String reason = request != null ? request.getReason() : null;
        InterviewScheduleResponse response = interviewScheduleService.declineInterview(userId, interviewId, reason);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{interviewId}/complete")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InterviewScheduleResponse> completeInterview(
            @PathVariable Long interviewId,
            @RequestParam(required = false) String notes) {
        Long userId = getCurrentUserId();
        InterviewScheduleResponse response = interviewScheduleService.completeInterview(userId, interviewId, notes);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{interviewId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InterviewScheduleResponse> cancelInterview(@PathVariable Long interviewId) {
        Long userId = getCurrentUserId();
        InterviewScheduleResponse response = interviewScheduleService.cancelInterview(userId, interviewId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<InterviewScheduleResponse>> getMyInterviews() {
        Long userId = getCurrentUserId();
        List<InterviewScheduleResponse> responses = interviewScheduleService.getMyInterviews(userId);
        return ResponseEntity.ok(responses);
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }

        String principalName = auth.getName();
        try {
            return Long.parseLong(principalName);
        } catch (NumberFormatException ignored) {
            User user = userRepository.findByEmail(principalName).orElse(null);
            return user != null ? user.getId() : null;
        }
    }
}