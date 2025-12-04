package com.exe.skillverse_backend.mentor_booking_service.dto.response;

import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    private Long id;
    private Long mentorId;
    private Long learnerId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer durationMinutes;
    private BookingStatus status;
    private BigDecimal priceVnd;
    private String meetingLink;
    private String paymentReference;
    
    // Enhanced fields for UI
    private String mentorName;
    private String mentorAvatar;
    private String learnerName;
    private String learnerAvatar;
}

