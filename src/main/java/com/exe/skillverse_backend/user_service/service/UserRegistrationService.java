package com.exe.skillverse_backend.user_service.service;

import com.exe.skillverse_backend.shared.service.RegistrationService;
import com.exe.skillverse_backend.user_service.dto.request.UserRegistrationRequest;
import com.exe.skillverse_backend.user_service.dto.response.UserRegistrationResponse;

public interface UserRegistrationService extends RegistrationService<UserRegistrationRequest, UserRegistrationResponse> {
}
