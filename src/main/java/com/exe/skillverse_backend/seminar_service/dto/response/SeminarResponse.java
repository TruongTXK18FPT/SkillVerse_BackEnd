package com.exe.skillverse_backend.seminar_service.dto.response;

import com.exe.skillverse_backend.seminar_service.entity.SeminarStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeminarResponse {
    private Long id;
    private String title;
    private String description;
    private String imageUrl;
    private String meetingLink; // Only visible if bought or creator
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal price;
    private SeminarStatus status;
    private String creatorId;
    private String creatorName; // Need to fetch user profile
    private String creatorAvatar;
    @JsonProperty("isOwned")
    private boolean isOwned; // True if current user bought it
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Capacity fields
    private Integer maxCapacity; // null = unlimited
    private Integer ticketsSold;
    private Integer remainingCapacity; // null = unlimited
    @JsonProperty("isSoldOut")
    private boolean isSoldOut;
}
