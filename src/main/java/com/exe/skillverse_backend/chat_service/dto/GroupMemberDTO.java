package com.exe.skillverse_backend.chat_service.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for group chat member information
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GroupMemberDTO {
    private Long userId;
    private String userName;
    private String email;
    private String avatarUrl;
    private String role; // MENTOR, STUDENT
    private LocalDateTime joinedAt;
    private boolean isOnline;
}
