package com.exe.skillverse_backend.community_service.dto.response;

import com.exe.skillverse_backend.community_service.entity.PostStatus;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class PostResponse {
    private Long id;
    private Long userId;
    private String userFullName;
    private String userAvatar;
    private String title;
    private String content;
    private String thumbnailUrl;
    private String category;
    private java.util.List<String> tags;
    private PostStatus status;
    private Integer likeCount;
    private Integer dislikeCount;
    private Integer commentCount;
    private Integer viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
