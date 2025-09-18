package com.exe.skillverse_backend.admin_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogDto {
    private Long id;
    private Long userId;
    private String userEmail;
    private String userFullName;
    private String action;
    private String objectType;
    private Long objectId;
    private String details;
    private LocalDateTime timestamp;
}