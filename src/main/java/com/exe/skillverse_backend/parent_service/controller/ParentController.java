package com.exe.skillverse_backend.parent_service.controller;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.parent_service.dto.request.LinkStudentRequest;
import com.exe.skillverse_backend.parent_service.dto.request.UpdateLinkStatusRequest;
import com.exe.skillverse_backend.parent_service.dto.response.ParentDashboardResponse;
import com.exe.skillverse_backend.parent_service.dto.response.ParentStudentLinkResponse;
import com.exe.skillverse_backend.parent_service.service.ParentService;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import com.exe.skillverse_backend.ai_service.dto.response.RoadmapSessionSummary;
import com.exe.skillverse_backend.ai_service.dto.ChatSessionSummary;
import com.exe.skillverse_backend.ai_service.dto.ChatMessageResponse;
import com.exe.skillverse_backend.parent_service.dto.response.LearningReportResponse;

@RestController
@RequestMapping("/api/parents")
@RequiredArgsConstructor
@Tag(name = "Parent Service", description = "APIs for Parent role functionalities")
public class ParentController {

    private final ParentService parentService;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equalsIgnoreCase(authentication.getName())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Unauthenticated");
        }
        String principalName = authentication.getName();
        
        try {
            Long userId = Long.parseLong(principalName);
            return userRepository.findById(userId)
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
        } catch (NumberFormatException e) {
            // Fallback if principal is not an ID (e.g. email)
            return userRepository.findByEmail(principalName)
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
        }
    }

    @PostMapping("/link")
    @Operation(summary = "Send link request to a student")
    public ResponseEntity<ParentStudentLinkResponse> sendLinkRequest(@RequestBody LinkStudentRequest request) {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(parentService.sendLinkRequest(currentUser.getId(), request));
    }

    @PutMapping("/link/{linkId}")
    @Operation(summary = "Update link status (Accept/Reject)")
    public ResponseEntity<ParentStudentLinkResponse> updateLinkStatus(
            @PathVariable Long linkId,
            @RequestBody UpdateLinkStatusRequest request) {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(parentService.updateLinkStatus(currentUser.getId(), linkId, request));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Get Parent Dashboard")
    public ResponseEntity<ParentDashboardResponse> getParentDashboard() {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(parentService.getParentDashboard(currentUser.getId()));
    }

    @GetMapping("/student-links")
    @Operation(summary = "Get links for the current student")
    public ResponseEntity<List<ParentStudentLinkResponse>> getStudentLinks() {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(parentService.getStudentLinks(currentUser.getId()));
    }

    @GetMapping("/sent-requests")
    @Operation(summary = "Get sent link requests (for parent)")
    public ResponseEntity<List<ParentStudentLinkResponse>> getSentLinkRequests() {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(parentService.getSentLinkRequests(currentUser.getId()));
    }

    @DeleteMapping("/link/{linkId}")
    @Operation(summary = "Unlink parent-student connection")
    public ResponseEntity<Void> unlink(@PathVariable Long linkId) {
        User currentUser = getCurrentUser();
        parentService.unlink(currentUser.getId(), linkId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/student/{studentId}/roadmaps")
    @Operation(summary = "Get roadmaps of a linked student")
    public ResponseEntity<List<RoadmapSessionSummary>> getStudentRoadmaps(@PathVariable Long studentId) {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(parentService.getStudentRoadmaps(currentUser.getId(), studentId));
    }

    @GetMapping("/student/{studentId}/chat-sessions")
    @Operation(summary = "Get chat sessions of a linked student")
    public ResponseEntity<List<ChatSessionSummary>> getStudentChatSessions(@PathVariable Long studentId) {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(parentService.getStudentChatSessions(currentUser.getId(), studentId));
    }

    @GetMapping("/student/{studentId}/chat-sessions/{sessionId}")
    @Operation(summary = "Get chat session details of a linked student")
    public ResponseEntity<List<ChatMessageResponse>> getStudentChatSessionDetails(
            @PathVariable Long studentId,
            @PathVariable Long sessionId) {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(parentService.getStudentChatSessionDetails(currentUser.getId(), studentId, sessionId));
    }

    @PostMapping("/student/{studentId}/learning-report")
    @Operation(summary = "Generate AI-powered learning report for a linked student")
    public ResponseEntity<LearningReportResponse> generateLearningReport(@PathVariable Long studentId) {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(parentService.generateLearningReport(currentUser.getId(), studentId));
    }

    @GetMapping("/student/{studentId}/learning-reports")
    @Operation(summary = "Get learning report history for a linked student")
    public ResponseEntity<java.util.List<LearningReportResponse>> getLearningReportHistory(@PathVariable Long studentId) {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(parentService.getLearningReportHistory(currentUser.getId(), studentId));
    }

    @GetMapping("/student/{studentId}/learning-reports/latest")
    @Operation(summary = "Get the most recent learning report for a linked student")
    public ResponseEntity<LearningReportResponse> getLatestLearningReport(@PathVariable Long studentId) {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(parentService.getLatestLearningReport(currentUser.getId(), studentId));
    }
}
