package com.exe.skillverse_backend.community_service.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class CommentResponse {
    private Long id;
    private Long postId;
    private Long userId;
    private String userFullName;
    private String userAvatar;
    private String content;
    private Long parentId;
    private LocalDateTime createdAt;
    private boolean hidden;
    private Integer reportCount;
    private String moderationNote;
}
