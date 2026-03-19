package com.exe.skillverse_backend.business_service.dto.response;

import com.exe.skillverse_backend.business_service.entity.enums.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO cho recruitment message
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecruitmentMessageResponse {

    private Long id;

    private Long sessionId;

    // Sender info
    private Long senderId;
    private String senderName;
    private String senderAvatar;
    private String senderRole; // RECRUITER hoặc CANDIDATE

    // Message content
    private String content;
    private MessageType messageType;

    // Action info (cho message đặc biệt)
    private String actionType;
    private String actionData;

    // Read status
    private Boolean isRead;
    private LocalDateTime readAt;

    // Timestamp
    private LocalDateTime createdAt;
}
