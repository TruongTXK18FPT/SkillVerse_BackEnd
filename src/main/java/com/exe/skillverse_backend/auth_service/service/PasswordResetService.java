package com.exe.skillverse_backend.auth_service.service;

import com.exe.skillverse_backend.auth_service.dto.request.ChangePasswordRequest;
import com.exe.skillverse_backend.auth_service.dto.request.ResetPasswordRequest;
import com.exe.skillverse_backend.auth_service.dto.request.SetPasswordRequest;
import com.exe.skillverse_backend.auth_service.dto.response.ForgotPasswordResponse;
import com.exe.skillverse_backend.auth_service.dto.response.RegistrationResponse;

public interface PasswordResetService {

    ForgotPasswordResponse initiateForgotPassword(String email);

    RegistrationResponse resetPassword(ResetPasswordRequest request);

    RegistrationResponse setPasswordForGoogleUser(Long userId, SetPasswordRequest request);

    RegistrationResponse changePassword(Long userId, ChangePasswordRequest request);
}
