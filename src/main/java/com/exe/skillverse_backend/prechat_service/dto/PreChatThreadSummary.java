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
public class PreChatThreadSummary {
    private Long counterpartId;
    private String counterpartName;
    private String counterpartAvatar;
    private String lastContent;
    private LocalDateTime lastTime;
    private long unreadCount;
    private boolean isMyRoleMentor;
}

