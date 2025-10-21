package com.exe.skillverse_backend.portfolio_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Portfolio Extended Profile - stores portfolio-specific user information
 * Complements the basic UserProfile from user_service with portfolio features
 */
@Entity
@Table(name = "portfolio_extended_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioExtendedProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @MapsId
    private User user;

    // Portfolio-specific professional information
    @Column(name = "professional_title", length = 200)
    private String professionalTitle; // e.g., "Full Stack Developer", "UI/UX Designer"

    @Column(name = "career_goals", columnDefinition = "TEXT")
    private String careerGoals; // Career aspirations and goals

    @Column(name = "years_of_experience")
    private Integer yearsOfExperience; // Total years of professional experience

    // Portfolio media files
    @Column(name = "avatar_url")
    private String avatarUrl; // Cloudinary avatar URL (portfolio-specific, different from basic profile)

    @Column(name = "avatar_public_id")
    private String avatarPublicId; // Cloudinary public ID for deletion

    @Column(name = "video_intro_url")
    private String videoIntroUrl; // Video introduction URL

    @Column(name = "video_intro_public_id")
    private String videoIntroPublicId; // Cloudinary public ID for video

    @Column(name = "cover_image_url")
    private String coverImageUrl; // Portfolio cover/banner image

    @Column(name = "cover_image_public_id")
    private String coverImagePublicId;

    // Professional links
    @Column(name = "linkedin_url")
    private String linkedinUrl;

    @Column(name = "github_url")
    private String githubUrl;

    @Column(name = "portfolio_website_url")
    private String portfolioWebsiteUrl;

    @Column(name = "behance_url")
    private String behanceUrl;

    @Column(name = "dribbble_url")
    private String dribbbleUrl;

    // Additional portfolio information
    @Column(name = "tagline", length = 300)
    private String tagline; // Short professional tagline/slogan

    @Column(name = "location")
    private String location; // City, Country (portfolio display)

    @Column(name = "availability_status", length = 50)
    private String availabilityStatus; // "Available", "Busy", "Not Available"

    @Column(name = "hourly_rate")
    private Double hourlyRate; // For freelancing

    @Column(name = "preferred_currency", length = 10)
    private String preferredCurrency; // USD, VND, EUR, etc.

    // Skills and languages as JSON arrays
    @Column(name = "top_skills", columnDefinition = "TEXT")
    private String topSkills; // JSON array of primary skills: ["Java", "React", "Spring Boot"]

    @Column(name = "languages_spoken", columnDefinition = "TEXT")
    private String languagesSpoken; // JSON array: ["Vietnamese", "English", "Japanese"]

    // Portfolio visibility and settings
    @Column(name = "is_public")
    @Builder.Default
    private Boolean isPublic = true; // Portfolio visibility to public

    @Column(name = "show_contact_info")
    @Builder.Default
    private Boolean showContactInfo = false; // Show email/phone in portfolio

    @Column(name = "allow_job_offers")
    @Builder.Default
    private Boolean allowJobOffers = true; // Allow recruiters to contact

    @Column(name = "theme_preference", length = 50)
    private String themePreference; // "light", "dark", "purple", etc.

    // Portfolio statistics
    @Column(name = "portfolio_views")
    @Builder.Default
    private Long portfolioViews = 0L;

    @Column(name = "total_projects")
    @Builder.Default
    private Integer totalProjects = 0;

    @Column(name = "total_certificates")
    @Builder.Default
    private Integer totalCertificates = 0;

    // SEO and discovery
    @Column(name = "custom_url_slug", unique = true)
    private String customUrlSlug; // e.g., /portfolio/john-doe-developer

    @Column(name = "meta_description", length = 500)
    private String metaDescription; // For SEO

    @Column(name = "keywords", columnDefinition = "TEXT")
    private String keywords; // JSON array for searchability

    // Timestamps
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Helper methods
    public void incrementPortfolioViews() {
        this.portfolioViews = (this.portfolioViews != null ? this.portfolioViews : 0L) + 1;
    }

    public void updateProjectCount(int count) {
        this.totalProjects = count;
    }

    public void updateCertificateCount(int count) {
        this.totalCertificates = count;
    }
}
