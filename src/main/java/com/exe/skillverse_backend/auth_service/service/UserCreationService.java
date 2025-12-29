package com.exe.skillverse_backend.auth_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import java.time.LocalDateTime;

public interface UserCreationService {

    User createUserForMentor(String email, String password, String fullName);

    User createUserForRecruiter(String email, String password, String fullName, String phone);

    User createUserForParent(String email, String password, String fullName, String phone);

    User createUserForUser(String email, String password, String fullName);

    boolean emailExists(String email);

    String generateOtpForUser(String email);

    LocalDateTime getOtpExpiryTime(String email);
}
