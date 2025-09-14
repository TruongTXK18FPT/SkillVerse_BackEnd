package com.exe.skillverse_backend.user_service.dto.request;

import com.exe.skillverse_backend.shared.dto.request.BaseRegistrationRequest;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserRegistrationRequest extends BaseRegistrationRequest {

    // Additional user-specific fields
    @Size(max = 1000, message = "Social links must not exceed 1000 characters")
    private String socialLinks;
}