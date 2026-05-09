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
    private String canonicalKey;
    private String description;
    private Long parentSkillId;
    private com.exe.skillverse_backend.shared.enums.SkillStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
