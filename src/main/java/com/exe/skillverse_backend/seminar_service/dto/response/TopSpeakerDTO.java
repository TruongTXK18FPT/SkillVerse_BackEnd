package com.exe.skillverse_backend.seminar_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for top speaker information in analytics
 * Represents a recruiter/company ranked by ticket sales
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopSpeakerDTO {

    /**
     * Creator user ID (recruiter ID)
     */
    private String creatorId;

    /**
     * Company name from RecruiterProfile
     * Falls back to "Unknown Company" if profile not found or null
     * NOTE: RecruiterProfile has companyName (@NotBlank), not avatar field
     */
    private String companyName;

    /**
     * Total tickets sold across all seminars by this recruiter
     * Aggregated from SeminarTicket table
     */
    private Long totalTicketsSold;
}
