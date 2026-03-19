package com.exe.skillverse_backend.shared.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillDto {
    private Long id;
    private String name;
    private String category;
    private String description;
    private Long parentSkillId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}