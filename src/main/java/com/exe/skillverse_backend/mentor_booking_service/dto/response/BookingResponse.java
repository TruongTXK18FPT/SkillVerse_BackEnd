package com.exe.skillverse_backend.mentor_booking_service.dto.response;

import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    private Long id;
    private Long mentorId;
    private Long learnerId;
    private LocalDateTime createdAt;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer durationMinutes;
    private BookingStatus status;
    private BigDecimal priceVnd;
    private String meetingLink;
    private String paymentReference;
    private Boolean confirmedByLearner;
    private LocalDateTime mentorCompletedAt;
    private LocalDateTime learnerConfirmedAt;
    private LocalDateTime learnerCompletedAt;
    private LocalDateTime completionDeadline;

    // Enhanced fields for UI
    private String mentorName;
    private String mentorAvatar;
    private String learnerName;
    private String learnerAvatar;

    // Dispute info
    private Long disputeId;

    // Chat availability
    private Boolean chatAllowed;

    // V3 Phase 1: optional node/journey context — null for legacy bookings
    private Long journeyId;
    private Long roadmapSessionId;
    private String nodeId;
    private Long nodeSkillId;
    private String bookingType;

    // V3 Phase 2: ROADMAP_MENTORING tracking — null for non-roadmap bookings
    private LocalDateTime roadmapMentoringStartedAt;
    private Integer verificationAttempts;
    private LocalDateTime nextVerifyAllowedAt;
}
