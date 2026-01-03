package com.exe.skillverse_backend.chat_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GroupChatResponse {
    private Long id;
    private Long courseId;
    private Long mentorId;
    private String mentorName;
    private String name;
    private String avatarUrl;
    private LocalDateTime createdAt;
    private boolean isMember;
    
    /**
     * Total number of members in the group
     */
    private int memberCount;
    
    /**
     * List of members (populated when requesting detailed info)
     */
    private java.util.List<GroupMemberDTO> members;
}
