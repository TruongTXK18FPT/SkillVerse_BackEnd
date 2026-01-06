package com.exe.skillverse_backend.seminar_service.dto.response;

import com.exe.skillverse_backend.seminar_service.entity.SeminarStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeminarTicketResponse {
    private Long id;
    private Long seminarId;
    private String seminarTitle;
    private String seminarImageUrl;
    private SeminarStatus seminarStatus;
    private LocalDateTime seminarStartTime;
    private LocalDateTime seminarEndTime;
    private String meetingLink;
    private String userId;
    private BigDecimal pricePaid;
    private LocalDateTime purchasedAt;
}
