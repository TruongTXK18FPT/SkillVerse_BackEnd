package com.exe.skillverse_backend.user_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.user_service.dto.request.AddSkillRequest;
import com.exe.skillverse_backend.user_service.dto.request.UpdateProfileRequest;
import com.exe.skillverse_backend.user_service.dto.request.UpdateSkillRequest;
import com.exe.skillverse_backend.user_service.dto.response.UserProfileResponse;
import com.exe.skillverse_backend.user_service.dto.response.UserSkillResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface UserProfileService {
    UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request);

    UserProfileResponse getProfile(Long userId);

    UserProfileResponse getProfileByEmail(String email);

    List<UserProfileResponse> getProfilesByRegion(String region);

    List<UserProfileResponse> searchProfilesByName(String name);

    String uploadAvatar(Long userId, MultipartFile file);

    UserSkillResponse addSkill(Long userId, AddSkillRequest request);

    UserSkillResponse updateSkill(Long userId, Long skillId, UpdateSkillRequest request);

    void removeSkill(Long userId, Long skillId);

    List<UserSkillResponse> getUserSkills(Long userId);

    List<UserSkillResponse> getUserSkillsByCategory(Long userId, String category);

    List<UserSkillResponse> getUserSkillsByMinProficiency(Long userId, Integer minProficiency);

    UserProfileResponse createCompleteProfile(Long userId, String fullName, String phone, String address,
            String region, String bio, Long avatarMediaId, Long companyId,
            String socialLinks);

    boolean hasProfile(Long userId);

    void createUserProfileForGoogleUser(User user, String name, String email, String picture);
}
