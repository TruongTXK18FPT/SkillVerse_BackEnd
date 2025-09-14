package com.exe.skillverse_backend.auth_service.dto.response;

import lombok.Data;
import java.util.Set;

@Data
public class UserDto {
    private Long id;
    private String email;
    private String fullName;
    private Set<String> roles;
}
