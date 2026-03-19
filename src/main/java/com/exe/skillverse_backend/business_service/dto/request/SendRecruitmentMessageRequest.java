package com.exe.skillverse_backend.business_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO để gửi tin nhắn trong recruitment chat
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendRecruitmentMessageRequest {

    @NotNull(message = "Session ID is required")
    private Long sessionId;

    @NotBlank(message = "Content is required")
    private String content;

    /**
     * Loại tin nhắn (TEXT, IMAGE, GIF, EMOJI)
     */
    @Builder.Default
    private String messageType = "TEXT";

    /**
     * Action type cho tin nhắn đặc biệt
     * (INVITE_JOB, VIEW_PROFILE, ACCEPT_INVITE, REJECT_INVITE, MARK_INTERESTED, MOVE_TO_SCREENING)
     */
    private String actionType;

    /**
     * JSON data cho action (e.g., job info)
     */
    private String actionData;
}
