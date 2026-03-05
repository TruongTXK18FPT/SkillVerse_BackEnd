package com.exe.skillverse_backend.business_service.dto.response;

import com.exe.skillverse_backend.business_service.entity.JobReview;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobReviewResponse {

    private Long id;
    private Long applicationId;
    private String jobTitle;

    // Reviewer info
    private Long reviewerId;
    private String reviewerName;
    private String reviewerAvatar;

    // Reviewee info
    private Long revieweeId;
    private String revieweeName;
    private String revieweeAvatar;

    private JobReview.ReviewType reviewType;

    // Rating
    private Integer rating;
    private String comment;
    private String strengths;
    private String improvements;
    private String recommendations;

    // Specific ratings
    private Integer communicationRating;
    private Integer qualityRating;
    private Integer timelinessRating;
    private Integer professionalismRating;

    private Boolean isPublic;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
