package com.exe.skillverse_backend.meowl_chat_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for role-aware Meowl onboarding context.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeowlOnboardingContextResponse {

    private boolean success;
    private String language;
    private String activeRole;
    private List<String> availableRoles;
    private boolean roleSwitchEnabled;
    private boolean onboardingSeen;
    private LocalDateTime onboardingSeenAt;
    private String welcomeMessage;
    private String nextBestAction;
    private List<String> whatYouCanDo;
    private List<QuickAction> quickActions;
    private List<String> suggestedPrompts;
    private Map<String, String> contextSummary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuickAction {
        private String id;
        private String label;
        private String description;
        private String actionType; // NAVIGATE | PROMPT
        private String actionValue; // route path or prompt text
    }
}
