package com.exe.skillverse_backend.mentor_booking_service.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingReviewDTO {
    private Long id;
    private Long bookingId;
    private Long studentId;
    private String studentName;
    private String studentAvatar;
    private Long mentorId;
    private Integer rating;
    private String comment;
    private String reply;
    private boolean isAnonymous;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
