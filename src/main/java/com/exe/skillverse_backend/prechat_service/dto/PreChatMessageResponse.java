package com.exe.skillverse_backend.prechat_service.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

