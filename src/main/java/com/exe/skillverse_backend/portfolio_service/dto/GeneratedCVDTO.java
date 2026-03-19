package com.exe.skillverse_backend.portfolio_service.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedCVDTO {
    private Long id;
    private Long userId;
    private String cvContent;
    private String cvJson;
    private String templateName;
    private Boolean isActive;
    private Integer version;
    private Boolean generatedByAi;
    private String pdfUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
