package com.exe.skillverse_backend.shared.dto;

import com.exe.skillverse_backend.shared.enums.SkillSuggestionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillSuggestionDto {
    private Long id;
    private String suggestedName;
    private String suggestedCanonicalKey;
    private String description;
    private Long sourceUserId;
    private SkillSuggestionStatus status;
    private Long matchedSkillId;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
