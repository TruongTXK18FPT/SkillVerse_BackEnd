package com.exe.skillverse_backend.portfolio_service.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponseDto {

    private Long id;
    private Long userId;
    private String name;
    private String description;
    private String category;
    private String productUrl;
    private Long mediaId;
    private String mediaUrl; // For convenience when returning media info
    private LocalDate releaseDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}