package com.exe.skillverse_backend.auth_service.dto.response;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private String avatarUrl;
    private Set<String> roles;

    /**
     * Primary role of the user (USER, MENTOR, RECRUITER, PARENT, ADMIN)
     */
    private String primaryRole;

    /**
     * Primary authentication method (LOCAL or GOOGLE)
     */
    private String authProvider;

    /**
     * Whether user has linked their Google account.
     * If true, user can login with both password AND Google.
     */
    private boolean googleLinked;

    /**
     * Indicates whether the user's identity has been verified by an admin
     * (Currently applies to Mentors with CCCD).
     */
    private Boolean identityVerified;
}
