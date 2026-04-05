package com.exe.skillverse_backend.notification_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FcmTokenRequest {

    @NotBlank(message = "Device token is required")
    @Size(max = 500, message = "Device token too long")
    private String deviceToken;

    @Size(max = 20, message = "Device type too long")
    private String deviceType = "ANDROID";

    @Size(max = 100, message = "Device name too long")
    private String deviceName;
}
