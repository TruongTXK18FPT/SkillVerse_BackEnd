package com.exe.skillverse_backend.mentor_booking_service.controller;

import com.exe.skillverse_backend.mentor_booking_service.dto.request.RoadmapMentorNodeReorderRequest;
import com.exe.skillverse_backend.mentor_booking_service.dto.request.RoadmapMentorNodeUpsertRequest;
import com.exe.skillverse_backend.mentor_booking_service.dto.request.RoadmapMentorOverviewUpdateRequest;
import com.exe.skillverse_backend.mentor_booking_service.dto.response.BookingResponse;
import com.exe.skillverse_backend.mentor_booking_service.dto.response.RoadmapFollowUpMeetingDTO;
import com.exe.skillverse_backend.mentor_booking_service.dto.response.RoadmapMentorWorkspaceResponse;
import com.exe.skillverse_backend.mentor_booking_service.service.MentorRoadmapWorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the Mentor Roadmap Workspace hub.
 * Provides APIs for mentors to manage ROADMAP_MENTORING bookings,
 * refactor learner roadmaps, and schedule follow-up meetings.
 */
@RestController
@RequestMapping("/api/v1/mentor-roadmap-bookings")
@RequiredArgsConstructor
@Tag(name = "Mentor Roadmap Workspace", description = "Quản lý booking đồng hành roadmap và workspace mentor")
public class MentorRoadmapWorkspaceController {

    private final MentorRoadmapWorkspaceService workspaceService;

    // =====================================================================
    // Booking List
    // =====================================================================

    @GetMapping
    @Operation(summary = "Danh sách booking ROADMAP_MENTORING của mentor hiện tại")
    public ResponseEntity<List<BookingResponse>> getMentorRoadmapBookings(Authentication authentication) {
        Long mentorId = getUserId(authentication);
        return ResponseEntity.ok(workspaceService.getMentorRoadmapBookings(mentorId));
    }

    // =====================================================================
    // Workspace: Read
    // =====================================================================

    @GetMapping("/{bookingId}/workspace")
    @Operation(summary = "Lấy workspace roadmap của booking (mentor hoặc learner)")
    public ResponseEntity<RoadmapMentorWorkspaceResponse> getWorkspace(
            @PathVariable Long bookingId,
            Authentication authentication) {
        Long callerId = getUserId(authentication);
        return ResponseEntity.ok(workspaceService.getWorkspace(callerId, bookingId));
    }

    // =====================================================================
    // Workspace: Overview Update
    // =====================================================================

    @PutMapping("/{bookingId}/overview")
    @Operation(summary = "Mentor cập nhật overview, structure, thinking progression, next steps")
    public ResponseEntity<RoadmapMentorWorkspaceResponse> updateOverview(
            @PathVariable Long bookingId,
            @Valid @RequestBody RoadmapMentorOverviewUpdateRequest request,
            Authentication authentication) {
        Long mentorId = getUserId(authentication);
        return ResponseEntity.ok(workspaceService.updateOverview(mentorId, bookingId, request));
    }

    // =====================================================================
    // Workspace: Node CRUD
    // =====================================================================

    @PutMapping("/{bookingId}/nodes/{nodeId}")
    @Operation(summary = "Mentor cập nhật một node trong roadmap")
    public ResponseEntity<RoadmapMentorWorkspaceResponse> updateNode(
            @PathVariable Long bookingId,
            @PathVariable String nodeId,
            @Valid @RequestBody RoadmapMentorNodeUpsertRequest request,
            Authentication authentication) {
        Long mentorId = getUserId(authentication);
        return ResponseEntity.ok(workspaceService.updateNode(mentorId, bookingId, nodeId, request));
    }

    @PostMapping("/{bookingId}/nodes")
    @Operation(summary = "Mentor thêm node mới vào roadmap")
    public ResponseEntity<RoadmapMentorWorkspaceResponse> createNode(
            @PathVariable Long bookingId,
            @Valid @RequestBody RoadmapMentorNodeUpsertRequest request,
            Authentication authentication) {
        Long mentorId = getUserId(authentication);
        return ResponseEntity.ok(workspaceService.createNode(mentorId, bookingId, request));
    }

    @PutMapping("/{bookingId}/nodes/reorder")
    @Operation(summary = "Mentor sắp xếp lại thứ tự các node trong cùng parent")
    public ResponseEntity<RoadmapMentorWorkspaceResponse> reorderNodes(
            @PathVariable Long bookingId,
            @Valid @RequestBody RoadmapMentorNodeReorderRequest request,
            Authentication authentication) {
        Long mentorId = getUserId(authentication);
        return ResponseEntity.ok(workspaceService.reorderNodes(mentorId, bookingId, request));
    }

    // =====================================================================
    // Follow-Up Meetings
    // =====================================================================

    @GetMapping("/{bookingId}/follow-ups")
    @Operation(summary = "Danh sách follow-up meetings của booking (mentor hoặc learner)")
    public ResponseEntity<List<RoadmapFollowUpMeetingDTO>> getFollowUps(
            @PathVariable Long bookingId,
            Authentication authentication) {
        Long callerId = getUserId(authentication);
        return ResponseEntity.ok(workspaceService.getFollowUps(callerId, bookingId));
    }

    @PostMapping("/{bookingId}/follow-ups")
    @Operation(summary = "Mentor tạo follow-up meeting mới")
    public ResponseEntity<RoadmapFollowUpMeetingDTO> createFollowUp(
            @PathVariable Long bookingId,
            @Valid @RequestBody RoadmapFollowUpMeetingDTO request,
            Authentication authentication) {
        Long mentorId = getUserId(authentication);
        return ResponseEntity.ok(workspaceService.createFollowUp(mentorId, bookingId, request));
    }

    @PutMapping("/{bookingId}/follow-ups/{meetingId}")
    @Operation(summary = "Mentor cập nhật follow-up meeting")
    public ResponseEntity<RoadmapFollowUpMeetingDTO> updateFollowUp(
            @PathVariable Long bookingId,
            @PathVariable Long meetingId,
            @Valid @RequestBody RoadmapFollowUpMeetingDTO request,
            Authentication authentication) {
        Long mentorId = getUserId(authentication);
        return ResponseEntity.ok(workspaceService.updateFollowUp(mentorId, bookingId, meetingId, request));
    }

    @DeleteMapping("/{bookingId}/follow-ups/{meetingId}")
    @Operation(summary = "Mentor xóa follow-up meeting")
    public ResponseEntity<Void> deleteFollowUp(
            @PathVariable Long bookingId,
            @PathVariable Long meetingId,
            Authentication authentication) {
        Long mentorId = getUserId(authentication);
        workspaceService.deleteFollowUp(mentorId, bookingId, meetingId);
        return ResponseEntity.noContent().build();
    }

    // =====================================================================
    // Utility
    // =====================================================================

    private Long getUserId(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return Long.valueOf(jwt.getClaimAsString("userId"));
    }
}
