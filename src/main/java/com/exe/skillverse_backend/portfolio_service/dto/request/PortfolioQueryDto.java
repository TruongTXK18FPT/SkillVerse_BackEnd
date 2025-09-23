package com.exe.skillverse_backend.portfolio_service.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioQueryDto {

    // Common filters
    private String search; // Search across title, description, technologies
    private List<String> technologies;
    private List<String> categories; // For products and other items with categories
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isPublic;

    // Project-specific filters
    private String projectStatus; // IN_PROGRESS, COMPLETED, ON_HOLD
    private String githubUrl;
    private String liveUrl;

    // Product-specific filters
    private String productType; // PHYSICAL, DIGITAL, SERVICE
    private Double minPrice;
    private Double maxPrice;
    private String availability; // AVAILABLE, OUT_OF_STOCK, DISCONTINUED

    // Certificate-specific filters
    private String issuer;
    private String certificateType;
    private Boolean hasExpiryDate;
    private LocalDate expiryAfter;
    private LocalDate expiryBefore;

    // Pagination
    private Integer page;
    private Integer size;
    private String sortBy; // title, date, createdAt, etc.
    private String sortDirection; // ASC, DESC

    // Type filter for combined queries
    private String itemType; // PROJECT, PRODUCT, CERTIFICATE
}