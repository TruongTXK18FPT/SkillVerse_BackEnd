package com.exe.skillverse_backend.community_service.dto.request;

import com.exe.skillverse_backend.community_service.entity.PostStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PostCreateRequest {
    @NotBlank
    @Size(max = 255)
    private String title;

    @NotBlank
    private String content;

    private String thumbnailUrl;

    private String category;

    private java.util.List<String> tags;

    private PostStatus status;
}
