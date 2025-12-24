package com.exe.skillverse_backend.admin_service.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
