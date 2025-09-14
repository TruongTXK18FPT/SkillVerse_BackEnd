package com.exe.skillverse_backend.user_service.dto.response;

import com.exe.skillverse_backend.shared.dto.response.BaseRegistrationResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserRegistrationResponse extends BaseRegistrationResponse {

    // Additional user-specific response fields if needed
}