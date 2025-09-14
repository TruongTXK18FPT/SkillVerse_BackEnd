package com.exe.skillverse_backend.user_service.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(max = 255, message = "Full name must not exceed 255 characters")
    private String fullName;

    private Long avatarMediaId;

    @Size(max = 1000, message = "Bio must not exceed 1000 characters")
    private String bio;

    @Size(max = 20, message = "Phone must not exceed 20 characters")
    private String phone;

    @Size(max = 500, message = "Address must not exceed 500 characters")
    private String address;

    @Size(max = 100, message = "Region must not exceed 100 characters")
    private String region;

    private Long companyId;

    @Size(max = 1000, message = "Social links must not exceed 1000 characters")
    private String socialLinks; // JSON string
}