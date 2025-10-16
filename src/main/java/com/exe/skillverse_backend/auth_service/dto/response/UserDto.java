package com.exe.skillverse_backend.auth_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private Set<String> roles;

    /**
     * Primary authentication method (LOCAL or GOOGLE)
     */
    private String authProvider;

    /**
     * Whether user has linked their Google account.
     * If true, user can login with both password AND Google.
     */
    private boolean googleLinked;
}
