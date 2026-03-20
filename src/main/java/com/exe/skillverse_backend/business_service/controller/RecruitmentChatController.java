package com.exe.skillverse_backend.business_service.controller;

import com.exe.skillverse_backend.business_service.dto.request.CreateRecruitmentSessionRequest;
import com.exe.skillverse_backend.business_service.dto.request.SendRecruitmentMessageRequest;
import com.exe.skillverse_backend.business_service.dto.request.UpdateRecruitmentStatusRequest;
import com.exe.skillverse_backend.business_service.dto.response.RecruitmentMessageResponse;
import com.exe.skillverse_backend.business_service.dto.response.RecruitmentSessionResponse;
import com.exe.skillverse_backend.business_service.entity.enums.RecruitmentJobContextType;
import com.exe.skillverse_backend.business_service.service.RecruitmentChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller cho recruitment chat giữa recruiter và candidate
 * Base path: /api/v1/recruitment
 */
@RestController
@RequestMapping("/api/v1/recruitment")
@Slf4j
@RequiredArgsConstructor
public class RecruitmentChatController {

    private final RecruitmentChatService recruitmentChatService;

    /**
     * POST /api/v1/recruitment/sessions - Tạo mới recruitment session
     * Recruiter chủ động tiếp cận candidate
     */
    @PostMapping("/sessions")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<RecruitmentSessionResponse> createSession(
            @Valid @RequestBody CreateRecruitmentSessionRequest request,
            Authentication authentication) {

        Long recruiterId = Long.parseLong(authentication.getName());
        log.info("POST /api/v1/recruitment/sessions - Recruiter {} creating session with candidate {}",
                recruiterId, request.getCandidateId());

        RecruitmentSessionResponse response = recruitmentChatService.createSession(recruiterId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/v1/recruitment/sessions - Lấy danh sách session của recruiter
     */
    @GetMapping("/sessions")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<Page<RecruitmentSessionResponse>> getRecruiterSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "lastMessageAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            Authentication authentication) {

        Long recruiterId = Long.parseLong(authentication.getName());
        log.info("GET /api/v1/recruitment/sessions - Fetching sessions for recruiter {}", recruiterId);

        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<RecruitmentSessionResponse> response = recruitmentChatService.getRecruiterSessions(recruiterId, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/recruitment/my-sessions - Lấy danh sách session của candidate (user)
     */
    @GetMapping("/my-sessions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<RecruitmentSessionResponse>> getCandidateSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "lastMessageAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("GET /api/v1/recruitment/my-sessions - Fetching sessions for candidate {}", userId);

        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<RecruitmentSessionResponse> response = recruitmentChatService.getCandidateSessions(userId, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/recruitment/sessions/search - Tìm kiếm session
     */
    @GetMapping("/sessions/search")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<Page<RecruitmentSessionResponse>> searchSessions(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        Long recruiterId = Long.parseLong(authentication.getName());
        log.info("GET /api/v1/recruitment/sessions/search - Searching sessions for recruiter {}", recruiterId);

        Pageable pageable = PageRequest.of(page, size);
        Page<RecruitmentSessionResponse> response = recruitmentChatService.searchRecruiterSessions(recruiterId, query, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/recruitment/sessions/job/{jobId} - Lấy session theo job
     */
    @GetMapping("/sessions/job/{jobId}")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<List<RecruitmentSessionResponse>> getSessionsByJob(
            @PathVariable Long jobId,
            Authentication authentication) {

        Long recruiterId = Long.parseLong(authentication.getName());
        log.info("GET /api/v1/recruitment/sessions/job/{} - Fetching sessions for job", jobId);

        List<RecruitmentSessionResponse> response = recruitmentChatService.getSessionsByJob(recruiterId, jobId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/recruitment/sessions/{sessionId} - Lấy chi tiết session
     */
    @GetMapping("/sessions/{sessionId}")
    @PreAuthorize("hasAnyRole('RECRUITER', 'USER')")
    public ResponseEntity<RecruitmentSessionResponse> getSessionById(
            @PathVariable Long sessionId,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("GET /api/v1/recruitment/sessions/{} - Fetching session", sessionId);

        RecruitmentSessionResponse response = recruitmentChatService.getSessionById(userId, sessionId);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/recruitment/messages - Gửi tin nhắn
     */
    @PostMapping("/messages")
    @PreAuthorize("hasAnyRole('RECRUITER', 'USER')")
    public ResponseEntity<RecruitmentMessageResponse> sendMessage(
            @Valid @RequestBody SendRecruitmentMessageRequest request,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("POST /api/v1/recruitment/messages - User {} sending message to session {}",
                userId, request.getSessionId());

        RecruitmentMessageResponse response = recruitmentChatService.sendMessage(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/v1/recruitment/sessions/{sessionId}/messages - Lấy tin nhắn của session
     */
    @GetMapping("/sessions/{sessionId}/messages")
    @PreAuthorize("hasAnyRole('RECRUITER', 'USER')")
    public ResponseEntity<Page<RecruitmentMessageResponse>> getSessionMessages(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("GET /api/v1/recruitment/sessions/{}/messages - Fetching messages", sessionId);

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<RecruitmentMessageResponse> response = recruitmentChatService.getSessionMessages(userId, sessionId, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * PUT /api/v1/recruitment/sessions/{sessionId}/read - Đánh dấu tin nhắn đã đọc
     */
    @PutMapping("/sessions/{sessionId}/read")
    @PreAuthorize("hasAnyRole('RECRUITER', 'USER')")
    public ResponseEntity<Void> markMessagesAsRead(
            @PathVariable Long sessionId,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("PUT /api/v1/recruitment/sessions/{}/read - Marking messages as read", sessionId);

        recruitmentChatService.markMessagesAsRead(userId, sessionId);
        return ResponseEntity.ok().build();
    }

    /**
     * PUT /api/v1/recruitment/sessions/{sessionId}/status - Cập nhật trạng thái session
     */
    @PutMapping("/sessions/{sessionId}/status")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<RecruitmentSessionResponse> updateSessionStatus(
            @PathVariable Long sessionId,
            @Valid @RequestBody UpdateRecruitmentStatusRequest request,
            Authentication authentication) {

        Long recruiterId = Long.parseLong(authentication.getName());
        log.info("PUT /api/v1/recruitment/sessions/{}/status - Updating status to {}", sessionId, request.getStatus());

        RecruitmentSessionResponse response = recruitmentChatService.updateSessionStatus(recruiterId, sessionId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/v1/recruitment/sessions/{sessionId} - Archive session
     */
    @DeleteMapping("/sessions/{sessionId}")
    @PreAuthorize("hasAnyRole('RECRUITER', 'USER')")
    public ResponseEntity<Void> archiveSession(
            @PathVariable Long sessionId,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("DELETE /api/v1/recruitment/sessions/{} - Archiving session", sessionId);

        recruitmentChatService.archiveSession(userId, sessionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/v1/recruitment/unread-count - Lấy số tin nhắn chưa đọc
     */
    @GetMapping("/unread-count")
    @PreAuthorize("hasAnyRole('RECRUITER', 'USER')")
    public ResponseEntity<Map<String, Long>> getUnreadCount(Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("GET /api/v1/recruitment/unread-count - Fetching unread count for user {}", userId);

        long count = recruitmentChatService.getUnreadCount(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * POST /api/v1/recruitment/sessions/get-or-create - Tạo hoặc lấy session hiện có
     * Dùng để redirect từ candidate search sang chat
     */
    @PostMapping("/sessions/get-or-create")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<RecruitmentSessionResponse> getOrCreateSession(
            @RequestParam Long candidateId,
            @RequestParam(required = false) Long jobId,
            @RequestParam(defaultValue = "JOB_POSTING") RecruitmentJobContextType jobContextType,
            @RequestParam(defaultValue = "MANUAL") String sourceType,
            Authentication authentication) {

        Long recruiterId = Long.parseLong(authentication.getName());
        log.info("POST /api/v1/recruitment/sessions/get-or-create - Recruiter {} with candidate {} job {}",
                recruiterId, candidateId, jobId);

        com.exe.skillverse_backend.business_service.entity.enums.RecruitmentSessionSource source =
                com.exe.skillverse_backend.business_service.entity.enums.RecruitmentSessionSource.valueOf(sourceType);

        RecruitmentSessionResponse response = recruitmentChatService.getOrCreateSession(
                recruiterId,
                candidateId,
                jobId,
                source,
                jobContextType);
        return ResponseEntity.ok(response);
    }
}
