package com.exe.skillverse_backend.portfolio_service.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Combined DTO that includes:
 * 1. Basic profile info from user_service UserProfile
 * 2. Extended portfolio info from portfolio_service PortfolioExtendedProfile
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserProfileDTO {
    // Primary key
    private Long userId;
    
    // ===== BASIC PROFILE INFO (from user_service.UserProfile) =====
    @JsonAlias({"displayName"})
    private String fullName; // From basic profile
    private String basicBio; // Bio from basic profile
    private String phone; // From basic profile
    private String address; // From basic profile
    private String region; // From basic profile
    private Long avatarMediaId; // Media ID from basic profile
    private String basicAvatarUrl; // Avatar URL from basic profile (via Media entity)
    private Long companyId; // From basic profile
    private String socialLinks; // JSON from basic profile
    
    // ===== EXTENDED PORTFOLIO INFO (from portfolio_service.PortfolioExtendedProfile) =====
    private String professionalTitle; // e.g., "Full Stack Developer"
    private String careerGoals;
    private Integer yearsOfExperience;
    
    // Portfolio media (separate from basic profile avatar)
    private String portfolioAvatarUrl; // Portfolio-specific avatar
    private String videoIntroUrl;
    private String coverImageUrl;
    
    // Professional links
    private String linkedinUrl;
    private String githubUrl;
    private String portfolioWebsiteUrl;
    private String behanceUrl;
    private String dribbbleUrl;
    
    // Additional portfolio info
    private String tagline;
    private String location;
    private String availabilityStatus;
    private Double hourlyRate;
    private String preferredCurrency;
    
    // Skills and languages (JSON arrays as strings)
    private String topSkills; // JSON: ["Java", "React"]
    private String languagesSpoken; // JSON: ["Vietnamese", "English"]
    
    // Portfolio settings
    private Boolean isPublic;
    private Boolean showContactInfo;
    private Boolean allowJobOffers;
    private String themePreference;
    
    // Portfolio stats
    private Long portfolioViews;
    private Integer totalProjects;
    private Integer totalCertificates;
    
    // SEO
    private String customUrlSlug;
    private String metaDescription;
    private String keywords; // JSON array
    
    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Helper method to get display name
    public String getDisplayName() {
        return fullName != null ? fullName : "User " + userId;
    }
    
    // Helper method to get display bio
    public String getDisplayBio() {
        return basicBio != null ? basicBio : "";
    }
    
    // Helper method to get primary avatar (prefer portfolio avatar, fallback to basic)
    public String getPrimaryAvatarUrl() {
        return portfolioAvatarUrl != null ? portfolioAvatarUrl : basicAvatarUrl;
    }
}
