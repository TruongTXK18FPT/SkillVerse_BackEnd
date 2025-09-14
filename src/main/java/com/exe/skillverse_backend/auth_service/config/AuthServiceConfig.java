package com.exe.skillverse_backend.auth_service.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = {
    "com.exe.skillverse_backend.auth_service",
    "com.exe.skillverse_backend.user_service",
    "com.exe.skillverse_backend.shared"
})
public class AuthServiceConfig {
    // This ensures that UserProfileService is available to AuthService
}