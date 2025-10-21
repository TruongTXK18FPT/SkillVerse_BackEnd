package com.exe.skillverse_backend.portfolio_service.dto;

import lombok.*;
import java.time.LocalDateTime;

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
