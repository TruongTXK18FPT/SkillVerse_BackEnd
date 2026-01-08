package com.exe.skillverse_backend.seminar_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for seminar analytics data
 * Contains aggregate statistics and top speakers leaderboard
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeminarAnalyticsDTO {

    /**
     * Total seminars (KÊNH): ACCEPTED + OPEN + CLOSED
     * Excludes DRAFT, PENDING, REJECTED
     */
    private Integer totalSeminars;

    /**
     * Active seminars (HOẠT ĐỘNG): ACCEPTED + OPEN only
     * Currently selling tickets, not yet closed
     */
    private Integer activeSeminars;

    /**
     * Completed seminars (HOÀN THÀNH): CLOSED only
     * Already finished events
     */
    private Integer completedSeminars;

    /**
     * Top speakers ranked by total tickets sold
     * Maximum 4 speakers, ordered by totalTicketsSold DESC
     */
    private List<TopSpeakerDTO> topSpeakers;
}
