package com.exe.skillverse_backend.notification_service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FcmTokenResponse {
    private Long id;
    private String deviceToken;
    private String deviceType;
    private String deviceName;
    private boolean active;
    private String message;
}
