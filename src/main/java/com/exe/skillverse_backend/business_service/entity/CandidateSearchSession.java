package com.exe.skillverse_backend.business_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity to track recruiter candidate searches for analytics
 */
@Entity
@Table(name = "candidate_search_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateSearchSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recruiter_id", nullable = false)
    private Long recruiterId;

    @Column(name = "search_query", columnDefinition = "TEXT")
    private String searchQuery;

    @Column(name = "filters", columnDefinition = "jsonb")
    private String filters; // JSON string of filters

    @Column(name = "total_results", nullable = false)
    @Builder.Default
    private Integer totalResults = 0;

    @Column(name = "page_size", nullable = false)
    @Builder.Default
    private Integer pageSize = 20;

    @Column(name = "searched_at", nullable = false)
    private LocalDateTime searchedAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @PrePersist
    protected void onCreate() {
        if (searchedAt == null) {
            searchedAt = LocalDateTime.now();
        }
    }
}
