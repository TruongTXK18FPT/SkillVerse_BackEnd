package com.exe.skillverse_backend.business_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Rating summary cho profile
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRatingSummary {

    private Long userId;
    private String userName;
    private String userAvatar;

    private BigDecimal averageRating;
    private Integer totalReviews;
    private Integer totalCompletedJobs;
    
    // Breakdown by rating
    private Integer fiveStarCount;
    private Integer fourStarCount;
    private Integer threeStarCount;
    private Integer twoStarCount;
    private Integer oneStarCount;

    // Average specific ratings
    private BigDecimal averageCommunicationRating;
    private BigDecimal averageQualityRating;
    private BigDecimal averageTimelinessRating;
    private BigDecimal averageProfessionalismRating;

    // Completion rate
    private BigDecimal completionRate; // percentage
}
