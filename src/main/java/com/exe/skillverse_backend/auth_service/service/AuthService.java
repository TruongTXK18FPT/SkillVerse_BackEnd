package com.exe.skillverse_backend.auth_service.service;

import com.exe.skillverse_backend.auth_service.dto.request.LoginRequest;
import com.exe.skillverse_backend.auth_service.dto.response.AuthResponse;
import com.exe.skillverse_backend.auth_service.dto.response.RegistrationResponse;
import com.exe.skillverse_backend.auth_service.entity.User;

public interface AuthService {

    RegistrationResponse verifyEmailAndActivate(String email, String otp);

    AuthResponse login(LoginRequest request);

    String generateToken(User user);

    boolean verifyToken(String token);

    AuthResponse refreshToken(String refreshToken);

    void logout(String accessToken);

    String buildScope(User user);

    AuthResponse authenticateWithGoogle(String idToken, Boolean rememberMe);
}
