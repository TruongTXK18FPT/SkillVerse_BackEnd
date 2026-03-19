package com.exe.skillverse_backend.community_service.dto.request;

import com.exe.skillverse_backend.community_service.entity.PostStatus;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class PostUpdateRequest {
    @Size(max = 255)
    private String title;

    private String content;

    private String thumbnailUrl;

    private String category;

    private List<String> tags;

    private PostStatus status;
}
