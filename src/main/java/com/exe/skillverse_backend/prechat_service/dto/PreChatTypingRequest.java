package com.exe.skillverse_backend.prechat_service.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreChatTypingRequest {
    private Long targetUserId;
    private boolean typing;
}

