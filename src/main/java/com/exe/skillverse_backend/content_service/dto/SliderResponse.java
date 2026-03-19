package com.exe.skillverse_backend.content_service.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SliderResponse {
    private UUID id;
    private String title;
    private String description;
    private String imageUrl;
    private String ctaText;
    private String ctaLink;
    private Boolean isActive;
    private Boolean isLogin;
    private Integer displayOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
