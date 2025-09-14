package com.exe.skillverse_backend.user_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.shared.service.AuditService;
import com.exe.skillverse_backend.user_service.dto.request.AddSkillRequest;
import com.exe.skillverse_backend.user_service.dto.request.CreateProfileRequest;
import com.exe.skillverse_backend.user_service.dto.request.UpdateProfileRequest;
import com.exe.skillverse_backend.user_service.dto.request.UpdateSkillRequest;
import com.exe.skillverse_backend.user_service.dto.response.UserProfileResponse;
import com.exe.skillverse_backend.user_service.dto.response.UserSkillResponse;
import com.exe.skillverse_backend.user_service.entity.UserProfile;
import com.exe.skillverse_backend.user_service.entity.UserSkill;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
import com.exe.skillverse_backend.user_service.repository.UserSkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final UserSkillRepository userSkillRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional
    public UserProfileResponse createProfile(Long userId, CreateProfileRequest request) {
        try {
            // Check if user exists
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Check if profile already exists
            if (userProfileRepository.existsByUserId(userId)) {
                throw new RuntimeException("User profile already exists");
            }

            // Create profile
            UserProfile profile = UserProfile.builder()
                    .userId(userId)
                    .user(user)
                    .fullName(request.getFullName())
                    .avatarMediaId(request.getAvatarMediaId())
                    .bio(request.getBio())
                    .phone(request.getPhone())
                    .address(request.getAddress())
                    .region(request.getRegion())
                    .companyId(request.getCompanyId())
                    .socialLinks(request.getSocialLinks())
                    .build();

            profile = userProfileRepository.save(profile);

            // Log action
            auditService.logAction(userId, "CREATE", "USER_PROFILE", userId.toString(),
                    "User profile created for user: " + user.getEmail());

            return mapToProfileResponse(profile);

        } catch (Exception e) {
            auditService.logAction(userId, "CREATE_PROFILE_FAILED", "USER_PROFILE", userId.toString(),
                    "Failed to create profile: " + e.getMessage());
            throw e;
        }
    }

    @Transactional
    public UserProfileResponse createDefaultProfile(Long userId) {
        try {
            // Check if user exists
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Check if profile already exists
            if (userProfileRepository.existsByUserId(userId)) {
                // Return existing profile instead of throwing error
                return getProfile(userId);
            }

            // Create default profile with minimal information
            UserProfile profile = UserProfile.builder()
                    .userId(userId)
                    .user(user)
                    .fullName(null) // Will be set later by user
                    .avatarMediaId(null)
                    .bio(null)
                    .phone(null)
                    .address(null)
                    .region(null)
                    .companyId(null)
                    .socialLinks(null)
                    .build();

            profile = userProfileRepository.save(profile);

            // Log action
            auditService.logAction(userId, "CREATE", "USER_PROFILE", userId.toString(),
                    "Default user profile created for user: " + user.getEmail());

            return mapToProfileResponse(profile);

        } catch (Exception e) {
            auditService.logAction(userId, "CREATE_DEFAULT_PROFILE_FAILED", "USER_PROFILE", userId.toString(),
                    "Failed to create default profile: " + e.getMessage());
            throw e;
        }
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        try {
            UserProfile profile = userProfileRepository.findByUserId(userId)
                    .orElseThrow(() -> new RuntimeException("User profile not found"));

            // Update fields if provided
            if (request.getFullName() != null) {
                profile.setFullName(request.getFullName());
            }
            if (request.getAvatarMediaId() != null) {
                profile.setAvatarMediaId(request.getAvatarMediaId());
            }
            if (request.getBio() != null) {
                profile.setBio(request.getBio());
            }
            if (request.getPhone() != null) {
                profile.setPhone(request.getPhone());
            }
            if (request.getAddress() != null) {
                profile.setAddress(request.getAddress());
            }
            if (request.getRegion() != null) {
                profile.setRegion(request.getRegion());
            }
            if (request.getCompanyId() != null) {
                profile.setCompanyId(request.getCompanyId());
            }
            if (request.getSocialLinks() != null) {
                profile.setSocialLinks(request.getSocialLinks());
            }

            profile = userProfileRepository.save(profile);

            // Log action
            auditService.logAction(userId, "UPDATE", "USER_PROFILE", userId.toString(),
                    "User profile updated for user: " + profile.getUser().getEmail());

            return mapToProfileResponse(profile);

        } catch (Exception e) {
            auditService.logAction(userId, "UPDATE_PROFILE_FAILED", "USER_PROFILE", userId.toString(),
                    "Failed to update profile: " + e.getMessage());
            throw e;
        }
    }

    public UserProfileResponse getProfile(Long userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("User profile not found"));

        return mapToProfileResponse(profile);
    }

    public UserProfileResponse getProfileByEmail(String email) {
        UserProfile profile = userProfileRepository.findByUserEmail(email)
                .orElseThrow(() -> new RuntimeException("User profile not found"));

        return mapToProfileResponse(profile);
    }

    public List<UserProfileResponse> getProfilesByRegion(String region) {
        return userProfileRepository.findByRegion(region)
                .stream()
                .map(this::mapToProfileResponse)
                .collect(Collectors.toList());
    }

    public List<UserProfileResponse> searchProfilesByName(String name) {
        return userProfileRepository.findByFullNameContaining(name)
                .stream()
                .map(this::mapToProfileResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteProfile(Long userId) {
        try {
            UserProfile profile = userProfileRepository.findByUserId(userId)
                    .orElseThrow(() -> new RuntimeException("User profile not found"));

            // Delete all user skills first
            userSkillRepository.deleteByIdUserId(userId);

            // Delete profile
            userProfileRepository.delete(profile);

            // Log action
            auditService.logAction(userId, "DELETE", "USER_PROFILE", userId.toString(),
                    "User profile deleted for user: " + profile.getUser().getEmail());

        } catch (Exception e) {
            auditService.logAction(userId, "DELETE_PROFILE_FAILED", "USER_PROFILE", userId.toString(),
                    "Failed to delete profile: " + e.getMessage());
            throw e;
        }
    }

    // Skills Management

    @Transactional
    public UserSkillResponse addSkill(Long userId, AddSkillRequest request) {
        try {
            // Check if user exists
            if (!userRepository.existsById(userId)) {
                throw new RuntimeException("User not found");
            }

            // Check if skill association already exists
            if (userSkillRepository.existsByIdUserIdAndIdSkillId(userId, request.getSkillId())) {
                throw new RuntimeException("Skill already associated with user");
            }

            // Create user skill
            UserSkill userSkill = new UserSkill(userId, request.getSkillId(), request.getProficiency());
            userSkill = userSkillRepository.save(userSkill);

            // Log action
            auditService.logAction(userId, "ADD_SKILL", "USER_SKILL",
                    userId + ":" + request.getSkillId(),
                    "Skill added with proficiency: " + request.getProficiency());

            return mapToSkillResponse(userSkill);

        } catch (Exception e) {
            auditService.logAction(userId, "ADD_SKILL_FAILED", "USER_SKILL",
                    userId + ":" + request.getSkillId(),
                    "Failed to add skill: " + e.getMessage());
            throw e;
        }
    }

    @Transactional
    public UserSkillResponse updateSkill(Long userId, Long skillId, UpdateSkillRequest request) {
        try {
            UserSkill userSkill = userSkillRepository.findByIdUserIdAndIdSkillId(userId, skillId)
                    .orElseThrow(() -> new RuntimeException("User skill not found"));

            Integer oldProficiency = userSkill.getProficiency();
            userSkill.setProficiency(request.getProficiency());

            userSkill = userSkillRepository.save(userSkill);

            // Log action
            auditService.logAction(userId, "UPDATE_SKILL", "USER_SKILL",
                    userId + ":" + skillId,
                    "Skill proficiency updated from " + oldProficiency + " to " + request.getProficiency());

            return mapToSkillResponse(userSkill);

        } catch (Exception e) {
            auditService.logAction(userId, "UPDATE_SKILL_FAILED", "USER_SKILL",
                    userId + ":" + skillId,
                    "Failed to update skill: " + e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void removeSkill(Long userId, Long skillId) {
        try {
            if (!userSkillRepository.existsByIdUserIdAndIdSkillId(userId, skillId)) {
                throw new RuntimeException("User skill not found");
            }

            userSkillRepository.deleteByIdUserIdAndIdSkillId(userId, skillId);

            // Log action
            auditService.logAction(userId, "REMOVE_SKILL", "USER_SKILL",
                    userId + ":" + skillId,
                    "Skill removed from user");

        } catch (Exception e) {
            auditService.logAction(userId, "REMOVE_SKILL_FAILED", "USER_SKILL",
                    userId + ":" + skillId,
                    "Failed to remove skill: " + e.getMessage());
            throw e;
        }
    }

    public List<UserSkillResponse> getUserSkills(Long userId) {
        return userSkillRepository.findByIdUserId(userId)
                .stream()
                .map(this::mapToSkillResponse)
                .collect(Collectors.toList());
    }

    public List<UserSkillResponse> getUserSkillsByCategory(Long userId, String category) {
        return userSkillRepository.findByUserIdAndSkillCategory(userId, category)
                .stream()
                .map(this::mapToSkillResponse)
                .collect(Collectors.toList());
    }

    public List<UserSkillResponse> getUserSkillsByMinProficiency(Long userId, Integer minProficiency) {
        return userSkillRepository.findByUserIdAndMinProficiency(userId, minProficiency)
                .stream()
                .map(this::mapToSkillResponse)
                .collect(Collectors.toList());
    }

    // Helper methods

    private UserProfileResponse mapToProfileResponse(UserProfile profile) {
        return UserProfileResponse.builder()
                .userId(profile.getUserId())
                .email(profile.getUser().getEmail())
                .fullName(profile.getFullName())
                .avatarMediaId(profile.getAvatarMediaId())
                .avatarMediaUrl(profile.getAvatarMedia() != null ? profile.getAvatarMedia().getUrl() : null)
                .bio(profile.getBio())
                .phone(profile.getPhone())
                .address(profile.getAddress())
                .region(profile.getRegion())
                .companyId(profile.getCompanyId())
                .socialLinks(profile.getSocialLinks())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    private UserSkillResponse mapToSkillResponse(UserSkill userSkill) {
        return UserSkillResponse.builder()
                .userId(userSkill.getId().getUserId())
                .skillId(userSkill.getId().getSkillId())
                .skillName(userSkill.getSkill() != null ? userSkill.getSkill().getName() : null)
                .skillCategory(userSkill.getSkill() != null ? userSkill.getSkill().getCategory() : null)
                .skillDescription(userSkill.getSkill() != null ? userSkill.getSkill().getDescription() : null)
                .proficiency(userSkill.getProficiency())
                .proficiencyLabel(getProficiencyLabel(userSkill.getProficiency()))
                .build();
    }

    @Transactional
    public UserProfileResponse createCompleteProfile(Long userId, String fullName, String phone, String address, 
                                                   String region, String bio, Long avatarMediaId, Long companyId, 
                                                   String socialLinks) {
        try {
            // Check if user exists
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Check if profile already exists
            if (userProfileRepository.existsByUserId(userId)) {
                throw new RuntimeException("User profile already exists");
            }

            // Create complete profile with all provided information
            UserProfile profile = UserProfile.builder()
                    .userId(userId)
                    .user(user)
                    .fullName(fullName)
                    .avatarMediaId(avatarMediaId)
                    .bio(bio)
                    .phone(phone)
                    .address(address)
                    .region(region)
                    .companyId(companyId)
                    .socialLinks(socialLinks)
                    .build();

            profile = userProfileRepository.save(profile);

            // Log action
            auditService.logAction(userId, "CREATE", "USER_PROFILE", userId.toString(),
                    "Complete user profile created during registration for user: " + user.getEmail());

            return mapToProfileResponse(profile);

        } catch (Exception e) {
            auditService.logAction(userId, "CREATE_COMPLETE_PROFILE_FAILED", "USER_PROFILE", userId.toString(),
                    "Failed to create complete profile during registration: " + e.getMessage());
            throw e;
        }
    }

    public boolean hasProfile(Long userId) {
        return userProfileRepository.existsByUserId(userId);
    }

    private String getProficiencyLabel(Integer proficiency) {
        return switch (proficiency) {
            case 1 -> "Beginner";
            case 2 -> "Novice";
            case 3 -> "Intermediate";
            case 4 -> "Advanced";
            case 5 -> "Expert";
            default -> "Unknown";
        };
    }
}