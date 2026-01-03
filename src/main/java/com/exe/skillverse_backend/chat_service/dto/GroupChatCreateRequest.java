package com.exe.skillverse_backend.chat_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GroupChatCreateRequest {
    private Long courseId;
    private String name;
    private String avatarUrl;
}
