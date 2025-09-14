package com.exe.skillverse_backend.shared.service;

import com.exe.skillverse_backend.shared.dto.request.BaseRegistrationRequest;
import com.exe.skillverse_backend.shared.dto.response.BaseRegistrationResponse;

/**
 * Common interface for all registration services
 * Ensures consistent registration patterns across services
 */
public interface RegistrationService<TRequest extends BaseRegistrationRequest, TResponse extends BaseRegistrationResponse> {

    /**
     * Register a new user with role-specific profile
     * 
     * @param request Registration request with user data
     * @return Registration response with user details
     * @throws IllegalArgumentException if email already exists or validation fails
     */
    TResponse register(TRequest request);
}