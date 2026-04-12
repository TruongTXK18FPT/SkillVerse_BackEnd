package com.exe.skillverse_backend.prechat_service.controller;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.mentor_booking_service.entity.Booking;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingRepository;
import com.exe.skillverse_backend.mentor_service.entity.MentorProfile;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.prechat_service.dto.PreChatMessageRequest;
import com.exe.skillverse_backend.prechat_service.dto.PreChatMessageResponse;
import com.exe.skillverse_backend.prechat_service.dto.PreChatThreadSummary;
import com.exe.skillverse_backend.prechat_service.dto.PreChatTypingRequest;
import com.exe.skillverse_backend.prechat_service.entity.PreChatBlock;
import com.exe.skillverse_backend.prechat_service.entity.PreChatMessage;
import com.exe.skillverse_backend.prechat_service.entity.PreChatReport;
import com.exe.skillverse_backend.prechat_service.entity.PreChatThreadState;
import com.exe.skillverse_backend.prechat_service.repository.PreChatBlockRepository;
import com.exe.skillverse_backend.prechat_service.repository.PreChatMessageRepository;
import com.exe.skillverse_backend.prechat_service.repository.PreChatReportRepository;
import com.exe.skillverse_backend.prechat_service.repository.PreChatThreadStateRepository;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.exe.skillverse_backend.user_service.entity.UserProfile;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prechat")
@RequiredArgsConstructor
@Tag(name = "Mentor Booking Chat", description = "Chat giữa learner và mentor chỉ khi đã có booking")
@Slf4j
public class PreChatController {

    private static final EnumSet<BookingStatus> CHAT_ENABLED_STATUSES = EnumSet.of(
            BookingStatus.PENDING,
            BookingStatus.CONFIRMED,
            BookingStatus.ONGOING);

    private final PreChatMessageRepository messageRepository;
    private final PreChatBlockRepository blockRepository;
    private final UserRepository userRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final UserProfileRepository userProfileRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;
    private final PreChatThreadStateRepository threadStateRepository;
    private final BookingRepository bookingRepository;
    private final PreChatReportRepository reportRepository;

    @PostMapping("/send")
    @Operation(summary = "Gửi tin nhắn mentor chat theo booking")
    public PreChatMessageResponse sendRest(@Valid @RequestBody PreChatMessageRequest request,
            Authentication authentication) {
        Long currentUserId = extractCurrentUserId(authentication);
        return handleSendMessage(currentUserId, request);
    }

    @MessageMapping("/prechat")
    public void sendStomp(PreChatMessageRequest request, Authentication authentication) {
        Long currentUserId = extractCurrentUserId(authentication);
        PreChatMessageResponse response = handleSendMessage(currentUserId, request);
        messagingTemplate.convertAndSendToUser(response.getMentorId().toString(), "/queue/prechat", response);
        messagingTemplate.convertAndSendToUser(response.getLearnerId().toString(), "/queue/prechat", response);
    }

    @GetMapping("/history")
    @Operation(summary = "Lấy lịch sử chat cũ theo mentor/learner")
    public Page<PreChatMessageResponse> getHistory(@RequestParam Long mentorId, @RequestParam int page,
            @RequestParam int size, Authentication authentication) {
        Long learnerId = extractCurrentUserId(authentication);
        User mentor = userRepository.findById(mentorId).orElseThrow();
        User learner = userRepository.findById(learnerId).orElseThrow();
        LocalDateTime now = LocalDateTime.now();
        return messageRepository.findByMentorAndLearnerOrderByCreatedAtAsc(mentor, learner, PageRequest.of(page, size))
                .map(message -> toResponse(message,
                        message.getBooking() != null && isChatAllowed(message.getBooking(), now)));
    }

    @GetMapping("/conversation")
    @Operation(summary = "Lấy nội dung cuộc trò chuyện theo booking")
    public Page<PreChatMessageResponse> getConversation(@RequestParam Long bookingId, @RequestParam int page,
            @RequestParam int size, Authentication authentication) {
        Long currentUserId = extractCurrentUserId(authentication);
        Booking booking = getAccessibleBookingOrThrow(currentUserId, bookingId);
        boolean chatEnabled = isChatAllowed(booking, LocalDateTime.now());
        return messageRepository.findByBookingOrderByCreatedAtAsc(booking, PageRequest.of(page, size))
                .map(message -> toResponse(message, chatEnabled));
    }

    @GetMapping("/threads")
    @Operation(summary = "Danh sách thread mentor chat đang mở theo booking")
    public List<PreChatThreadSummary> getThreads(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long userId = extractCurrentUserId(authentication);
        LocalDateTime now = LocalDateTime.now();
        List<Booking> eligibleBookings = bookingRepository.findChatEligibleBookings(userId, CHAT_ENABLED_STATUSES, now);
        List<PreChatThreadSummary> summaries = new ArrayList<>();

        for (Booking booking : eligibleBookings) {
            boolean isMyRoleMentor = booking.getMentor().getId().equals(userId);
            User counterpart = isMyRoleMentor ? booking.getLearner() : booking.getMentor();
            Optional<PreChatMessage> lastMessage = messageRepository.findTopByBookingOrderByCreatedAtDesc(booking);
            long unreadCount = isMyRoleMentor
                    ? messageRepository.countByBookingAndSenderAndReadByMentorFalse(booking, counterpart)
                    : messageRepository.countByBookingAndSenderAndReadByLearnerFalse(booking, counterpart);

            summaries.add(PreChatThreadSummary.builder()
                    .bookingId(booking.getId())
                    .counterpartId(counterpart.getId())
                    .counterpartName(resolveDisplayName(counterpart.getId(), counterpart))
                    .counterpartAvatar(resolveDisplayAvatar(counterpart.getId(), counterpart))
                    .lastContent(lastMessage.map(PreChatMessage::getContent)
                            .orElse("Booking đã tạo. Bạn có thể trao đổi trong khung chat này."))
                    .lastTime(lastMessage.map(PreChatMessage::getCreatedAt).orElse(booking.getCreatedAt()))
                    .unreadCount(unreadCount)
                    .isMyRoleMentor(isMyRoleMentor)
                    .bookingStartTime(booking.getStartTime())
                    .bookingEndTime(booking.getEndTime())
                    .bookingStatus(booking.getStatus())
                    .chatEnabled(isChatAllowed(booking, now))
                    .build());
        }

        summaries.sort((left, right) -> right.getLastTime().compareTo(left.getLastTime()));
        int from = Math.max(0, page * size);
        int to = Math.min(summaries.size(), from + size);
        if (from >= to) {
            return Collections.emptyList();
        }
        return summaries.subList(from, to);
    }

    @MessageMapping("/prechat.typing")
    public void typing(PreChatTypingRequest request, Authentication authentication) {
        Long senderId = extractCurrentUserId(authentication);
        Map<String, Object> payload = new HashMap<>();
        payload.put("senderId", senderId);
        payload.put("typing", request.isTyping());
        messagingTemplate.convertAndSendToUser(request.getTargetUserId().toString(), "/queue/prechat.typing", payload);
    }

    @PutMapping("/block/{userId}")
    @Operation(summary = "Mentor block learner khỏi mentor chat")
    public void blockUser(@PathVariable Long userId, Authentication authentication) {
        Long mentorId = extractCurrentUserId(authentication);
        User mentor = userRepository.findById(mentorId).orElseThrow();
        User learner = userRepository.findById(userId).orElseThrow();
        if (!blockRepository.existsByMentorAndLearner(mentor, learner)) {
            blockRepository.save(PreChatBlock.builder().mentor(mentor).learner(learner).build());
        }
    }

    @PutMapping("/block-mentor/{mentorId}")
    @Operation(summary = "Learner block mentor khỏi mentor chat")
    public void blockMentor(@PathVariable Long mentorId, Authentication authentication) {
        Long learnerId = extractCurrentUserId(authentication);
        User mentor = userRepository.findById(mentorId).orElseThrow();
        User learner = userRepository.findById(learnerId).orElseThrow();
        if (!blockRepository.existsByMentorAndLearner(mentor, learner)) {
            blockRepository.save(PreChatBlock.builder().mentor(mentor).learner(learner).build());
        }
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Đếm tin chưa đọc theo booking")
    public long unreadCount(@RequestParam Long bookingId, Authentication authentication) {
        Long userId = extractCurrentUserId(authentication);
        Booking booking = getAccessibleBookingOrThrow(userId, bookingId);
        User counterpart = booking.getMentor().getId().equals(userId) ? booking.getLearner() : booking.getMentor();
        if (booking.getMentor().getId().equals(userId)) {
            return messageRepository.countByBookingAndSenderAndReadByMentorFalse(booking, counterpart);
        }
        return messageRepository.countByBookingAndSenderAndReadByLearnerFalse(booking, counterpart);
    }

    @PutMapping("/mark-read")
    @Operation(summary = "Đánh dấu đã đọc toàn bộ theo booking")
    @Transactional
    public int markRead(@RequestParam Long bookingId, Authentication authentication) {
        Long userId = extractCurrentUserId(authentication);
        Booking booking = getAccessibleBookingOrThrow(userId, bookingId);
        User currentUser = userRepository.findById(userId).orElseThrow();
        if (booking.getMentor().getId().equals(userId)) {
            return messageRepository.markMentorReadByBooking(booking, currentUser);
        }
        return messageRepository.markLearnerReadByBooking(booking, currentUser);
    }

    private PreChatMessageResponse handleSendMessage(Long currentUserId, PreChatMessageRequest request) {
        Booking booking = getAccessibleBookingOrThrow(currentUserId, request.getBookingId());
        LocalDateTime now = LocalDateTime.now();
        if (!isChatAllowed(booking, now)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Session chat đã đóng hoặc booking không còn hiệu lực");
        }

        User mentor = booking.getMentor();
        User learner = booking.getLearner();
        User sender = mentor.getId().equals(currentUserId) ? mentor : learner;
        User recipient = sender.getId().equals(mentor.getId()) ? learner : mentor;

        if (blockRepository.existsByMentorAndLearner(mentor, learner)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Bạn đã bị mentor chặn");
        }

        String sanitized = sanitizeContent(request.getContent());
        PreChatMessage saved = messageRepository.save(PreChatMessage.builder()
                .booking(booking)
                .mentor(mentor)
                .learner(learner)
                .sender(sender)
                .content(sanitized)
                .readByMentor(sender.getId().equals(mentor.getId()))
                .readByLearner(sender.getId().equals(learner.getId()))
                .build());

        boolean recipientMuted = false;
        PreChatThreadState threadState = threadStateRepository.findByMentorAndLearner(mentor, learner).orElse(null);
        if (threadState != null) {
            recipientMuted = sender.getId().equals(mentor.getId())
                    ? Boolean.TRUE.equals(threadState.getMutedForLearner())
                    : Boolean.TRUE.equals(threadState.getMutedForMentor());
        }

        if (!recipientMuted) {
            notificationService.createNotification(
                    recipient.getId(),
                    sender.getId().equals(mentor.getId()) ? "Tin nhắn mới từ mentor" : "Tin nhắn mới từ learner",
                    sanitized,
                    NotificationType.PRECHAT_MESSAGE,
                    booking.getId().toString(),
                    sender.getId());
        }

        return toResponse(saved, true);
    }

    private String sanitizeContent(String content) {
        if (content == null) {
            return "";
        }

        String sanitized = content.trim();
        return sanitized.replaceAll("(?i)\\b(badword|nsfw|terror|hate)\\b", "***");
    }

    private PreChatMessageResponse toResponse(PreChatMessage message, boolean chatEnabled) {
        User sender = message.getSender();
        return PreChatMessageResponse.builder()
                .id(message.getId())
                .bookingId(message.getBooking() != null ? message.getBooking().getId() : null)
                .mentorId(message.getMentor().getId())
                .learnerId(message.getLearner().getId())
                .senderId(sender.getId())
                .senderName(resolveDisplayName(sender.getId(), sender))
                .senderAvatar(resolveDisplayAvatar(sender.getId(), sender))
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .chatEnabled(chatEnabled)
                .build();
    }

    private Long extractCurrentUserId(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return Long.valueOf(jwt.getClaimAsString("userId"));
    }

    private Booking getAccessibleBookingOrThrow(Long userId, Long bookingId) {
        return bookingRepository.findAccessibleBooking(bookingId, userId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.FORBIDDEN,
                        "Bạn không có quyền truy cập booking chat này"));
    }

    private boolean isChatAllowed(Booking booking, LocalDateTime now) {
        return booking != null
                && booking.getEndTime() != null
                && booking.getEndTime().isAfter(now)
                && CHAT_ENABLED_STATUSES.contains(booking.getStatus());
    }

    private String resolveDisplayName(Long userId, User user) {
        if (user == null) {
            return "User #" + userId;
        }

        Optional<UserProfile> userProfile = userProfileRepository.findByUserId(userId);
        if (userProfile.isPresent()) {
            String fullName = userProfile.get().getFullName();
            if (fullName != null && !fullName.isBlank()) {
                return fullName.trim();
            }
        }

        Optional<MentorProfile> mentorProfile = mentorProfileRepository.findByUserId(userId);
        if (mentorProfile.isPresent()) {
            String fullName = mentorProfile.get().getFullName();
            if (fullName != null && !fullName.isBlank()) {
                return fullName.trim();
            }
        }

        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName().trim();
        }

        String firstName = user.getFirstName() != null ? user.getFirstName().trim() : "";
        String lastName = user.getLastName() != null ? user.getLastName().trim() : "";
        String builtName = (firstName + " " + lastName).trim();
        if (!builtName.isBlank()) {
            return builtName;
        }

        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            String[] emailParts = user.getEmail().split("@", 2);
            if (emailParts.length > 0 && !emailParts[0].isBlank()) {
                return emailParts[0];
            }
        }

        return "User #" + userId;
    }

    private String resolveDisplayAvatar(Long userId, User user) {
        Optional<UserProfile> userProfile = userProfileRepository.findByUserId(userId);
        if (userProfile.isPresent()) {
            UserProfile profile = userProfile.get();
            if (profile.getAvatarMedia() != null
                    && profile.getAvatarMedia().getUrl() != null
                    && !profile.getAvatarMedia().getUrl().isBlank()) {
                return profile.getAvatarMedia().getUrl();
            }
        }

        Optional<MentorProfile> mentorProfile = mentorProfileRepository.findByUserId(userId);
        if (mentorProfile.isPresent()) {
            String avatarUrl = mentorProfile.get().getAvatarUrl();
            if (avatarUrl != null && !avatarUrl.isBlank()) {
                return avatarUrl;
            }
        }

        if (user != null && user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank()) {
            return user.getAvatarUrl();
        }

        return null;
    }

    @DeleteMapping("/threads/{counterpartId}")
    @Operation(summary = "Ẩn thread (soft-delete) cho người hiện tại")
    @Transactional
    public void hideThread(@PathVariable Long counterpartId, Authentication authentication) {
        Long currentId = extractCurrentUserId(authentication);
        User current = userRepository.findById(currentId).orElseThrow();
        User other = userRepository.findById(counterpartId).orElseThrow();
        boolean currentIsMentor = mentorProfileRepository.existsByUserId(currentId);
        User mentor = currentIsMentor ? current : other;
        User learner = currentIsMentor ? other : current;
        PreChatThreadState state = threadStateRepository.findByMentorAndLearner(mentor, learner)
                .orElse(PreChatThreadState.builder().mentor(mentor).learner(learner).build());
        if (currentIsMentor) {
            state.setHiddenForMentor(true);
        } else {
            state.setHiddenForLearner(true);
        }
        threadStateRepository.save(state);
    }

    @PutMapping("/threads/{counterpartId}/restore")
    @Operation(summary = "Khôi phục thread đã ẩn")
    @Transactional
    public void restoreThread(@PathVariable Long counterpartId, Authentication authentication) {
        Long currentId = extractCurrentUserId(authentication);
        User current = userRepository.findById(currentId).orElseThrow();
        User other = userRepository.findById(counterpartId).orElseThrow();
        boolean currentIsMentor = mentorProfileRepository.existsByUserId(currentId);
        User mentor = currentIsMentor ? current : other;
        User learner = currentIsMentor ? other : current;
        PreChatThreadState state = threadStateRepository.findByMentorAndLearner(mentor, learner)
                .orElse(PreChatThreadState.builder().mentor(mentor).learner(learner).build());
        if (currentIsMentor) {
            state.setHiddenForMentor(false);
        } else {
            state.setHiddenForLearner(false);
        }
        threadStateRepository.save(state);
    }

    @PutMapping("/threads/{counterpartId}/mute")
    @Operation(summary = "Mute thread: tắt thông báo")
    @Transactional
    public void muteThread(@PathVariable Long counterpartId, Authentication authentication) {
        Long currentId = extractCurrentUserId(authentication);
        User current = userRepository.findById(currentId).orElseThrow();
        User other = userRepository.findById(counterpartId).orElseThrow();
        boolean currentIsMentor = mentorProfileRepository.existsByUserId(currentId);
        User mentor = currentIsMentor ? current : other;
        User learner = currentIsMentor ? other : current;
        PreChatThreadState state = threadStateRepository.findByMentorAndLearner(mentor, learner)
                .orElse(PreChatThreadState.builder().mentor(mentor).learner(learner).build());
        if (currentIsMentor) {
            state.setMutedForMentor(true);
        } else {
            state.setMutedForLearner(true);
        }
        threadStateRepository.save(state);
    }

    @PutMapping("/threads/{counterpartId}/unmute")
    @Operation(summary = "Unmute thread: bật thông báo")
    @Transactional
    public void unmuteThread(@PathVariable Long counterpartId, Authentication authentication) {
        Long currentId = extractCurrentUserId(authentication);
        User current = userRepository.findById(currentId).orElseThrow();
        User other = userRepository.findById(counterpartId).orElseThrow();
        boolean currentIsMentor = mentorProfileRepository.existsByUserId(currentId);
        User mentor = currentIsMentor ? current : other;
        User learner = currentIsMentor ? other : current;
        PreChatThreadState state = threadStateRepository.findByMentorAndLearner(mentor, learner)
                .orElse(PreChatThreadState.builder().mentor(mentor).learner(learner).build());
        if (currentIsMentor) {
            state.setMutedForMentor(false);
        } else {
            state.setMutedForLearner(false);
        }
        threadStateRepository.save(state);
    }

    @PostMapping("/threads/{counterpartId}/report")
    @Operation(summary = "Báo cáo nội dung chat")
    @Transactional
    public Long reportThread(@PathVariable Long counterpartId, @RequestBody Map<String, Object> body,
            Authentication authentication) {
        Long reporterId = extractCurrentUserId(authentication);
        String reason = String.valueOf(body.getOrDefault("reason", ""));
        Long messageId = null;
        Object rawMessageId = body.get("messageId");
        if (rawMessageId != null) {
            try {
                messageId = Long.valueOf(String.valueOf(rawMessageId));
            } catch (Exception ignored) {
            }
        }

        User reporter = userRepository.findById(reporterId).orElseThrow();
        User other = userRepository.findById(counterpartId).orElseThrow();
        boolean reporterIsMentor = mentorProfileRepository.existsByUserId(reporterId);
        User mentor = reporterIsMentor ? reporter : other;
        User learner = reporterIsMentor ? other : reporter;

        PreChatReport report = reportRepository.save(PreChatReport.builder()
                .mentor(mentor)
                .learner(learner)
                .reporter(reporter)
                .messageId(messageId)
                .reason(reason)
                .build());
        return report.getId();
    }

    @GetMapping("/reports")
    @Operation(summary = "Danh sách báo cáo (Admin)")
    public Page<PreChatReport> listReports(@RequestParam(required = false) PreChatReport.Status status,
            @PageableDefault(size = 20) Pageable pageable) {
        if (status == null) {
            return reportRepository.findAll(pageable);
        }
        return reportRepository.findByStatus(status, pageable);
    }

    @PutMapping("/reports/{id}/status")
    @Operation(summary = "Cập nhật trạng thái báo cáo (Admin)")
    @Transactional
    public void updateReportStatus(@PathVariable Long id, @RequestParam PreChatReport.Status status) {
        PreChatReport report = reportRepository.findById(id).orElseThrow();
        report.setStatus(status);
        reportRepository.save(report);
    }
}
