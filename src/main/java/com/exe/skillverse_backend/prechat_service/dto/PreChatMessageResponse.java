package com.exe.skillverse_backend.prechat_service.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreChatMessageResponse {
    private Long id;
    private Long mentorId;
    private Long learnerId;
    private Long senderId;
    private String content;
    private LocalDateTime createdAt;
}

