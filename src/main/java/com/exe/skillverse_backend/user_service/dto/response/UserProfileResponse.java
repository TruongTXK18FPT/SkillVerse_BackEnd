package com.exe.skillverse_backend.user_service.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserProfileResponse {

    private Long userId;
    private String email;
    private String fullName;
    private Long avatarMediaId;
    private String avatarMediaUrl;
    private String avatarPosition;
    private String bio;
    private String phone;
    private String address;
    private String region;
    private Long companyId;
    private String companyName;
    private String socialLinks;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}