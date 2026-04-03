package com.exe.skillverse_backend.admin_service.dto.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminJourneyListItemResponse {

    private Long journeyId;
    private Long userId;
    private String userName;
    private String userEmail;
    private String type;
    private String domain;
    private String industry;
    private String jobRole;
    private String goal;
    private String status;
    private Integer progressPercentage;
    private Long assessmentTestId;
    private String assessmentTestStatus;
    private Long questionBankId;
    private String questionBankTitle;
    private String questionSource;
    private Integer latestScore;
    private String evaluatedLevel;
    private Boolean roadmapReady;
    private Long roadmapSessionId;
    private Instant createdAt;
    private Instant lastActivityAt;
    private Instant evaluatedAt;
}
