package com.exe.skillverse_backend.community_service.dto.response;

import com.exe.skillverse_backend.community_service.entity.PostStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

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
    private List<String> tags;
    private PostStatus status;
    private Integer likeCount;
    private Integer dislikeCount;
    private Boolean likedByCurrentUser;
    private Boolean dislikedByCurrentUser;
    private Integer commentCount;
    private Integer viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
