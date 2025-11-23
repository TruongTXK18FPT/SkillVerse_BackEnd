package com.exe.skillverse_backend.admin_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating user profile information by admin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserProfileRequest {
    @NotNull(message = "User ID is required")
    private Long userId;
    
    private String firstName;
    
    private String lastName;
    
    @Email(message = "Invalid email format")
    private String email;
    
    private String phoneNumber;
    
    private String reason; // Optional reason for profile update
}
