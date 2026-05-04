package com.exe.skillverse_backend.admin_service.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for setting (replacing) sub-admin roles for a user.
 * The provided roles will REPLACE all existing sub-admin roles of the user.
 * Only sub-admin roles (USER_ADMIN, CONTENT_ADMIN, etc.) are allowed via this endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddRoleRequest {
    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Roles are required")
    private List<String> roles;
}
