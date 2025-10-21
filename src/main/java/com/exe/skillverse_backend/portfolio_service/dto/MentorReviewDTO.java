package com.exe.skillverse_backend.portfolio_service.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorReviewDTO {
    private Long id;
    private Long userId;
    private Long mentorId;
    private String mentorName;
    private String mentorTitle;
    private String mentorAvatarUrl;
    private String feedback;
    private String skillEndorsed;
    private Integer rating;
    private Boolean isVerified;
    private Boolean isPublic;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
