package com.exe.skillverse_backend.prechat_service.controller;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.mentor_service.entity.MentorProfile;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.prechat_service.dto.PreChatMessageRequest;
import com.exe.skillverse_backend.prechat_service.dto.PreChatMessageResponse;
import com.exe.skillverse_backend.prechat_service.dto.PreChatTypingRequest;
import com.exe.skillverse_backend.prechat_service.entity.PreChatBlock;
import com.exe.skillverse_backend.prechat_service.entity.PreChatMessage;
import com.exe.skillverse_backend.prechat_service.entity.PreChatThreadState;
import com.exe.skillverse_backend.prechat_service.repository.PreChatBlockRepository;
import com.exe.skillverse_backend.prechat_service.repository.PreChatMessageRepository;
import com.exe.skillverse_backend.prechat_service.repository.PreChatThreadStateRepository;
import com.exe.skillverse_backend.prechat_service.entity.PreChatReport;
import com.exe.skillverse_backend.prechat_service.repository.PreChatReportRepository;
import com.exe.skillverse_backend.premium_service.service.PremiumService;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingRepository;
import com.exe.skillverse_backend.mentor_booking_service.entity.Booking;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/prechat")
@RequiredArgsConstructor
@Tag(name = "Pre-Booking Chat", description = "Chat giữa learner và mentor trước khi booking")
@Slf4j
public class PreChatController {

    private final PreChatMessageRepository messageRepository;
    private final PreChatBlockRepository blockRepository;
    private final UserRepository userRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final PremiumService premiumService;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;
    private final PreChatThreadStateRepository threadStateRepository;
    private final BookingRepository bookingRepository;
    private final PreChatReportRepository reportRepository;

    @PostMapping("/send")
    @Operation(summary = "Learner gửi tin nhắn pre-chat (REST)")
    public PreChatMessageResponse sendRest(@Valid @RequestBody PreChatMessageRequest request, Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long learnerId = Long.valueOf(jwt.getClaimAsString("userId"));
        return handleSendMessage(learnerId, request, false);
    }

    @MessageMapping("/prechat")
    public void sendStomp(PreChatMessageRequest request, Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long learnerId = Long.valueOf(jwt.getClaimAsString("userId"));
        PreChatMessageResponse resp = handleSendMessage(learnerId, request, false);
        messagingTemplate.convertAndSendToUser(resp.getMentorId().toString(), "/queue/prechat", resp);
        messagingTemplate.convertAndSendToUser(resp.getLearnerId().toString(), "/queue/prechat", resp);
    }

    @GetMapping("/history")
    @Operation(summary = "Lấy lịch sử chat")
    public Page<PreChatMessageResponse> getHistory(@RequestParam Long mentorId, @RequestParam int page, @RequestParam int size, Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long learnerId = Long.valueOf(jwt.getClaimAsString("userId"));
        User mentor = userRepository.findById(mentorId).orElseThrow();
        User learner = userRepository.findById(learnerId).orElseThrow();
        return messageRepository.findByMentorAndLearnerOrderByCreatedAtAsc(mentor, learner, PageRequest.of(page, size))
                .map(this::toResponse);
    }

    @GetMapping("/conversation")
    @Operation(summary = "Lấy nội dung cuộc trò chuyện với một người dùng khác (2 chiều)")
    public Page<PreChatMessageResponse> getConversation(@RequestParam Long counterpartId, @RequestParam int page, @RequestParam int size, Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long currentUserId = Long.valueOf(jwt.getClaimAsString("userId"));
        return messageRepository.findConversation(currentUserId, counterpartId, PageRequest.of(page, size))
                .map(this::toResponse);
    }

    @GetMapping("/threads")
    @Operation(summary = "Danh sách thread gần nhất")
    public java.util.List<com.exe.skillverse_backend.prechat_service.dto.PreChatThreadSummary> getThreads(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));
        java.util.List<PreChatMessage> latest = messageRepository.findLastThreads(userId);
        java.util.List<com.exe.skillverse_backend.prechat_service.dto.PreChatThreadSummary> out = new java.util.ArrayList<>();
        for (PreChatMessage m : latest) {
            Long counterpartId = m.getMentor().getId().equals(userId) ? m.getLearner().getId() : m.getMentor().getId();
            User counterpart = userRepository.findById(counterpartId).orElse(null);
            String name = counterpart == null ? ("User #" + counterpartId) : ((counterpart.getFirstName() != null ? counterpart.getFirstName() : "") + (counterpart.getLastName() != null ? " " + counterpart.getLastName() : "")).trim();
            String avatar = counterpart != null ? counterpart.getAvatarUrl() : null;
            if (avatar == null && counterpart != null) {
                // Try to get from profile if available
                try {
                    if (mentorProfileRepository.existsByUserId(counterpartId)) {
                        MentorProfile mp = mentorProfileRepository.findById(counterpartId).orElse(null);
                        // MentorProfile doesn't store avatar directly usually, but let's check if we can get it from UserProfileService if needed
                        // For now, just rely on User entity avatarUrl which should be synced
                    }
                } catch (Exception e) {}
            }

            java.util.Optional<PreChatThreadState> stOpt = threadStateRepository.findByMentorAndLearner(m.getMentor(), m.getLearner());
            if (stOpt.isPresent()) {
                PreChatThreadState st = stOpt.get();
                if (m.getMentor().getId().equals(userId) && Boolean.TRUE.equals(st.getHiddenForMentor())) continue;
                if (m.getLearner().getId().equals(userId) && Boolean.TRUE.equals(st.getHiddenForLearner())) continue;
            }
            long unread;
            if (m.getMentor().getId().equals(userId)) {
                unread = messageRepository.countByMentorAndLearnerAndSenderAndReadByMentorFalse(m.getMentor(), m.getLearner(), m.getLearner());
            } else {
                unread = messageRepository.countByMentorAndLearnerAndSenderAndReadByLearnerFalse(m.getMentor(), m.getLearner(), m.getMentor());
            }
            out.add(com.exe.skillverse_backend.prechat_service.dto.PreChatThreadSummary.builder()
                    .counterpartId(counterpartId)
                    .counterpartName(name.isEmpty() ? ("User #" + counterpartId) : name)
                    .counterpartAvatar(avatar)
                    .lastContent(m.getContent())
                    .lastTime(m.getCreatedAt())
                    .unreadCount(unread)
                    .isMyRoleMentor(m.getMentor().getId().equals(userId))
                    .build());
        }
        int from = Math.max(0, page * size);
        int to = Math.min(out.size(), from + size);
        if (from >= to) return java.util.Collections.emptyList();
        return out.subList(from, to);
    }

    @MessageMapping("/prechat.typing")
    public void typing(PreChatTypingRequest req, Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long senderId = Long.valueOf(jwt.getClaimAsString("userId"));
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("senderId", senderId);
        payload.put("typing", req.isTyping());
        messagingTemplate.convertAndSendToUser(req.getTargetUserId().toString(), "/queue/prechat.typing", payload);
    }

    @PutMapping("/block/{userId}")
    @Operation(summary = "Mentor block user khỏi pre-chat")
    public void blockUser(@PathVariable Long userId, Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long mentorId = Long.valueOf(jwt.getClaimAsString("userId"));
        User mentor = userRepository.findById(mentorId).orElseThrow();
        User learner = userRepository.findById(userId).orElseThrow();
        if (!blockRepository.existsByMentorAndLearner(mentor, learner)) {
            blockRepository.save(PreChatBlock.builder().mentor(mentor).learner(learner).build());
        }
    }

    @PutMapping("/block-mentor/{mentorId}")
    @Operation(summary = "Learner block mentor khỏi pre-chat")
    public void blockMentor(@PathVariable Long mentorId, Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long learnerId = Long.valueOf(jwt.getClaimAsString("userId"));
        User mentor = userRepository.findById(mentorId).orElseThrow();
        User learner = userRepository.findById(learnerId).orElseThrow();
        if (!blockRepository.existsByMentorAndLearner(mentor, learner)) {
            blockRepository.save(PreChatBlock.builder().mentor(mentor).learner(learner).build());
        }
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Đếm tin chưa đọc theo cặp")
    public long unreadCount(@RequestParam Long mentorId, Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));
        User mentor = userRepository.findById(mentorId).orElseThrow();
        User learner = userRepository.findById(userId).orElseThrow();
        if (mentor.getId().equals(userId)) {
            return messageRepository.countByMentorAndLearnerAndSenderAndReadByMentorFalse(mentor, learner, learner);
        } else {
            return messageRepository.countByMentorAndLearnerAndSenderAndReadByLearnerFalse(mentor, learner, mentor);
        }
    }

    @PutMapping("/mark-read")
    @Operation(summary = "Đánh dấu đã đọc toàn bộ theo cặp")
    @Transactional
    public int markRead(@RequestParam Long mentorId, Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));
        User mentor = userRepository.findById(mentorId).orElseThrow();
        User learner = userRepository.findById(userId).orElseThrow();
        if (mentor.getId().equals(userId)) {
            return messageRepository.markMentorRead(mentor, learner);
        } else {
            return messageRepository.markLearnerRead(mentor, learner);
        }
    }

    @PostMapping("/mentor/send")
    @Operation(summary = "Mentor gửi tin nhắn pre-chat (REST)")
    public PreChatMessageResponse sendAsMentor(@RequestParam Long learnerId, @RequestBody java.util.Map<String, String> body, Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long mentorId = Long.valueOf(jwt.getClaimAsString("userId"));
        PreChatMessageRequest req = new PreChatMessageRequest();
        req.setMentorId(mentorId);
        req.setContent(body.getOrDefault("content", ""));
        PreChatMessageResponse resp = handleSendMessage(learnerId, req, true);
        messagingTemplate.convertAndSendToUser(resp.getMentorId().toString(), "/queue/prechat", resp);
        messagingTemplate.convertAndSendToUser(resp.getLearnerId().toString(), "/queue/prechat", resp);
        return resp;
    }

    private PreChatMessageResponse handleSendMessage(Long learnerId, PreChatMessageRequest request, boolean senderIsMentor) {
        User mentor = userRepository.findById(request.getMentorId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Mentor not found"));
        User learner = userRepository.findById(learnerId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Learner not found"));

        MentorProfile profile = mentorProfileRepository.findById(mentor.getId()).orElse(null);
        if (profile != null && Boolean.FALSE.equals(profile.getPreChatEnabled())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Mentor không cho phép chat trước");
        }

        if (blockRepository.existsByMentorAndLearner(mentor, learner)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Bạn đã bị mentor chặn");
        }

        String sanitized = sanitizeContent(request.getContent());
        PreChatMessage.PreChatMessageBuilder builder = PreChatMessage.builder()
                .mentor(mentor)
                .learner(learner)
                .content(sanitized);
        if (senderIsMentor) {
            builder.sender(mentor);
        } else {
            builder.sender(learner);
        }
        if (senderIsMentor) {
            builder.readByMentor(true).readByLearner(false);
        } else {
            builder.readByMentor(false).readByLearner(true);
        }
        PreChatMessage saved = messageRepository.save(builder.build());

        boolean recipientMuted = false;
        var ts = threadStateRepository.findByMentorAndLearner(mentor, learner).orElse(null);
        if (ts != null) {
            recipientMuted = senderIsMentor ? Boolean.TRUE.equals(ts.getMutedForLearner()) : Boolean.TRUE.equals(ts.getMutedForMentor());
        }
        if (!recipientMuted) {
            if (senderIsMentor) {
                notificationService.createNotification(
                        learner.getId(),
                        "Tin nhắn mới từ mentor",
                        sanitized,
                        NotificationType.PRECHAT_MESSAGE,
                        saved.getId().toString(),
                        mentor.getId()
                );
            } else {
                notificationService.createNotification(
                        mentor.getId(),
                        "Tin nhắn pre-chat mới",
                        sanitized,
                        NotificationType.PRECHAT_MESSAGE,
                        saved.getId().toString(),
                        learner.getId()
                );
            }
        }

        return toResponse(saved);
    }

    private String sanitizeContent(String content) {
        if (content == null) return "";
        String c = content.trim();
        c = c.replaceAll("(?i)\\b(badword|nsfw|terror|hate)\\b", "***");
        return c;
    }

    private PreChatMessageResponse toResponse(PreChatMessage msg) {
        return PreChatMessageResponse.builder()
                .id(msg.getId())
                .mentorId(msg.getMentor().getId())
                .learnerId(msg.getLearner().getId())
                .senderId(msg.getSender().getId())
                .content(msg.getContent())
                .createdAt(msg.getCreatedAt())
                .build();
    }

    @DeleteMapping("/threads/{counterpartId}")
    @Operation(summary = "Ẩn thread (soft-delete) cho người hiện tại")
    @Transactional
    public void hideThread(@PathVariable Long counterpartId, Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long currentId = Long.valueOf(jwt.getClaimAsString("userId"));
        User current = userRepository.findById(currentId).orElseThrow();
        User other = userRepository.findById(counterpartId).orElseThrow();
        boolean currentIsMentor = mentorProfileRepository.existsByUserId(currentId);
        User mentor = currentIsMentor ? current : other;
        User learner = currentIsMentor ? other : current;
        PreChatThreadState st = threadStateRepository.findByMentorAndLearner(mentor, learner)
                .orElse(PreChatThreadState.builder().mentor(mentor).learner(learner).build());
        if (currentIsMentor) st.setHiddenForMentor(true); else st.setHiddenForLearner(true);
        threadStateRepository.save(st);
    }

    @PutMapping("/threads/{counterpartId}/restore")
    @Operation(summary = "Khôi phục thread đã ẩn")
    @Transactional
    public void restoreThread(@PathVariable Long counterpartId, Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long currentId = Long.valueOf(jwt.getClaimAsString("userId"));
        User current = userRepository.findById(currentId).orElseThrow();
        User other = userRepository.findById(counterpartId).orElseThrow();
        boolean currentIsMentor = mentorProfileRepository.existsByUserId(currentId);
        User mentor = currentIsMentor ? current : other;
        User learner = currentIsMentor ? other : current;
        PreChatThreadState st = threadStateRepository.findByMentorAndLearner(mentor, learner)
                .orElse(PreChatThreadState.builder().mentor(mentor).learner(learner).build());
        if (currentIsMentor) st.setHiddenForMentor(false); else st.setHiddenForLearner(false);
        threadStateRepository.save(st);
    }

    @PutMapping("/threads/{counterpartId}/mute")
    @Operation(summary = "Mute thread: tắt thông báo")
    @Transactional
    public void muteThread(@PathVariable Long counterpartId, Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long currentId = Long.valueOf(jwt.getClaimAsString("userId"));
        User current = userRepository.findById(currentId).orElseThrow();
        User other = userRepository.findById(counterpartId).orElseThrow();
        boolean currentIsMentor = mentorProfileRepository.existsByUserId(currentId);
        User mentor = currentIsMentor ? current : other;
        User learner = currentIsMentor ? other : current;
        PreChatThreadState st = threadStateRepository.findByMentorAndLearner(mentor, learner)
                .orElse(PreChatThreadState.builder().mentor(mentor).learner(learner).build());
        if (currentIsMentor) st.setMutedForMentor(true); else st.setMutedForLearner(true);
        threadStateRepository.save(st);
    }

    @PutMapping("/threads/{counterpartId}/unmute")
    @Operation(summary = "Unmute thread: bật thông báo")
    @Transactional
    public void unmuteThread(@PathVariable Long counterpartId, Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long currentId = Long.valueOf(jwt.getClaimAsString("userId"));
        User current = userRepository.findById(currentId).orElseThrow();
        User other = userRepository.findById(counterpartId).orElseThrow();
        boolean currentIsMentor = mentorProfileRepository.existsByUserId(currentId);
        User mentor = currentIsMentor ? current : other;
        User learner = currentIsMentor ? other : current;
        PreChatThreadState st = threadStateRepository.findByMentorAndLearner(mentor, learner)
                .orElse(PreChatThreadState.builder().mentor(mentor).learner(learner).build());
        if (currentIsMentor) st.setMutedForMentor(false); else st.setMutedForLearner(false);
        threadStateRepository.save(st);
    }

    @PostMapping("/threads/{counterpartId}/report")
    @Operation(summary = "Báo cáo nội dung chat")
    @Transactional
    public Long reportThread(@PathVariable Long counterpartId, @RequestBody java.util.Map<String, Object> body, Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long reporterId = Long.valueOf(jwt.getClaimAsString("userId"));
        String reason = String.valueOf(body.getOrDefault("reason", ""));
        Long messageId = null;
        Object mid = body.get("messageId");
        if (mid != null) {
            try { messageId = Long.valueOf(String.valueOf(mid)); } catch (Exception ignored) {}
        }
        User reporter = userRepository.findById(reporterId).orElseThrow();
        User other = userRepository.findById(counterpartId).orElseThrow();
        boolean reporterIsMentor = mentorProfileRepository.existsByUserId(reporterId);
        User mentor = reporterIsMentor ? reporter : other;
        User learner = reporterIsMentor ? other : reporter;
        PreChatReport report = PreChatReport.builder()
                .mentor(mentor)
                .learner(learner)
                .reporter(reporter)
                .messageId(messageId)
                .reason(reason)
                .build();
        report = reportRepository.save(report);
        return report.getId();
    }

    @GetMapping("/reports")
    @Operation(summary = "Danh sách báo cáo (Admin)")
    public Page<PreChatReport> listReports(@RequestParam(required = false) PreChatReport.Status status, @org.springframework.data.web.PageableDefault(size = 20) org.springframework.data.domain.Pageable pageable) {
        if (status == null) return reportRepository.findAll(pageable);
        return reportRepository.findByStatus(status, pageable);
    }

    @PutMapping("/reports/{id}/status")
    @Operation(summary = "Cập nhật trạng thái báo cáo (Admin)")
    @Transactional
    public void updateReportStatus(@PathVariable Long id, @RequestParam PreChatReport.Status status) {
        PreChatReport r = reportRepository.findById(id).orElseThrow();
        r.setStatus(status);
        reportRepository.save(r);
    }
}
