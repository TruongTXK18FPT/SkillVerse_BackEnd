package com.exe.skillverse_backend.ai_knowledge_service.controller;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.exe.skillverse_backend.shared.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiKnowledgeUserResolver {
    private final UserRepository userRepository;

    public User resolve(Jwt jwt) {
        Long userId = JwtUtils.extractUserId(jwt);
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found: " + userId));
    }
}
