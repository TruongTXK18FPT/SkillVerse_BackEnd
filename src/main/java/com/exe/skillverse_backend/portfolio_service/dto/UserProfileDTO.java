package com.exe.skillverse_backend.portfolio_service.dto;

import com.exe.skillverse_backend.portfolio_service.validator.*;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    @NotBlank(message = "Họ và tên là bắt buộc.")
    @Size(min = 2, max = 100, message = "Họ và tên phải từ 2 đến 100 ký tự.")
    private String fullName;
    private String email;
    private String basicBio;
    @VietnamesePhone
    private String phone;
    private String address;
    private String region;
    private Long avatarMediaId;
    private String basicAvatarUrl;
    private Long companyId;
    private String socialLinks;
    
    // ===== EXTENDED PORTFOLIO INFO (from portfolio_service.PortfolioExtendedProfile) =====
    @NotBlank(message = "Chức danh là bắt buộc.")
    @Size(min = 2, max = 100, message = "Chức danh phải từ 2 đến 100 ký tự.")
    private String professionalTitle;
    @Size(max = 500, message = "Mục tiêu nghề nghiệp không được quá 500 ký tự.")
    private String careerGoals;
    @Min(value = 0, message = "Số năm kinh nghiệm không được âm.")
    private Integer yearsOfExperience;
    @Valid
    private List<PortfolioWorkExperienceDTO> workExperiences;
    @Valid
    private List<PortfolioEducationDTO> educationHistory;
    
    // Portfolio media (separate from basic profile avatar)
    private String portfolioAvatarUrl; // Portfolio-specific avatar
    private String videoIntroUrl;
    private String coverImageUrl;
    
    // Professional links
    @ValidLinkedInUrl
    private String linkedinUrl;
    @ValidGitHubUrl
    private String githubUrl;
    @ValidPortfolioUrl
    private String portfolioWebsiteUrl;
    @ValidBehanceUrl
    private String behanceUrl;
    @ValidDribbbleUrl
    private String dribbbleUrl;

    // Additional portfolio info
    @Size(max = 100, message = "Khẩu hiệu không được quá 100 ký tự.")
    private String tagline;
    @Size(max = 100, message = "Địa điểm không được quá 100 ký tự.")
    private String location;
    private String availabilityStatus;
    @Min(value = 0, message = "Mức giá theo giờ không được âm.")
    private Double hourlyRate;
    private Double roadmapMentoringPrice;
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
    @ValidSlug
    private String customUrlSlug;
    private String metaDescription;
    private String keywords; // JSON array

    // Achievements (JSON array - used for mentor accounts)
    private String achievements; // JSON array of achievements

    // Owner role for frontend tab visibility
    private String primaryRole; // From auth User.primaryRole (MENTOR, USER, RECRUITER, ADMIN)

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
