package com.exe.skillverse_backend.prechat_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreChatTypingRequest {
    private Long targetUserId;
    private boolean typing;
}

