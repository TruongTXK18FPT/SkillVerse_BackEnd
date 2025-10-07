package com.exe.skillverse_backend.mentor_service.service.impl;

import com.exe.skillverse_backend.mentor_service.dto.request.MentorProfileUpdateRequest;
import com.exe.skillverse_backend.mentor_service.dto.response.MentorProfileResponse;
import com.exe.skillverse_backend.mentor_service.entity.MentorProfile;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.mentor_service.service.MentorProfileService;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MentorProfileServiceImpl implements MentorProfileService {

    private static final String MENTOR_PROFILE_NOT_FOUND = "MENTOR_PROFILE_NOT_FOUND";
    
    private final MentorProfileRepository mentorProfileRepository;

    @Override
    @Transactional(readOnly = true)
    public MentorProfileResponse getMentorProfile(Long userId) {
        log.info("Getting mentor profile for user ID: {}", userId);
        
        MentorProfile profile = mentorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(MENTOR_PROFILE_NOT_FOUND));
        
        return mapToResponse(profile);
    }

    @Override
    @Transactional
    public MentorProfileResponse updateMentorProfile(Long userId, MentorProfileUpdateRequest request) {
        log.info("Updating mentor profile for user ID: {}", userId);
        
        MentorProfile profile = mentorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(MENTOR_PROFILE_NOT_FOUND));
        
        // Update fields if provided
        if (request.getFirstName() != null) {
            // Extract first name from full name or use provided first name
            String fullName = profile.getFullName();
            if (fullName != null && fullName.contains(" ")) {
                String[] nameParts = fullName.split(" ", 2);
                nameParts[0] = request.getFirstName();
                profile.setFullName(String.join(" ", nameParts));
            } else {
                profile.setFullName(request.getFirstName() + (request.getLastName() != null ? " " + request.getLastName() : ""));
            }
        }
        
        if (request.getLastName() != null) {
            String fullName = profile.getFullName();
            if (fullName != null && fullName.contains(" ")) {
                String[] nameParts = fullName.split(" ", 2);
                if (nameParts.length > 1) {
                    nameParts[1] = request.getLastName();
                } else {
                    nameParts = new String[]{nameParts[0], request.getLastName()};
                }
                profile.setFullName(String.join(" ", nameParts));
            } else {
                profile.setFullName((profile.getFullName() != null ? profile.getFullName() : "") + " " + request.getLastName());
            }
        }
        
        if (request.getEmail() != null) {
            profile.setEmail(request.getEmail());
        }
        
        if (request.getBio() != null) {
            profile.setPersonalProfile(request.getBio());
        }
        
        if (request.getSpecialization() != null) {
            profile.setMainExpertiseAreas(request.getSpecialization());
        }
        
        if (request.getExperience() != null) {
            profile.setYearsOfExperience(request.getExperience());
        }
        
        if (request.getAvatar() != null) {
            profile.setAvatarUrl(request.getAvatar());
        }
        
        if (request.getSocialLinks() != null) {
            if (request.getSocialLinks().getLinkedin() != null) {
                profile.setLinkedinProfile(request.getSocialLinks().getLinkedin());
            }
            if (request.getSocialLinks().getGithub() != null) {
                profile.setGithubProfile(request.getSocialLinks().getGithub());
            }
            if (request.getSocialLinks().getWebsite() != null) {
                profile.setWebsiteUrl(request.getSocialLinks().getWebsite());
            }
        }
        
        profile.setUpdatedAt(LocalDateTime.now());
        
        MentorProfile savedProfile = mentorProfileRepository.save(profile);
        log.info("Mentor profile updated successfully for user ID: {}", userId);
        
        return mapToResponse(savedProfile);
    }

    @Override
    @Transactional
    public String uploadMentorAvatar(Long userId, byte[] fileData, String fileName) {
        log.info("Uploading avatar for mentor user ID: {}", userId);
        
        MentorProfile profile = mentorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(MENTOR_PROFILE_NOT_FOUND));
        
        // Generate unique filename
        String fileExtension = fileName.substring(fileName.lastIndexOf("."));
        String uniqueFileName = "mentor_" + userId + "_" + UUID.randomUUID().toString() + fileExtension;
        
        // In a real implementation, you would upload to cloud storage (AWS S3, etc.)
        // For now, we'll just store a placeholder URL
        String avatarUrl = "/uploads/avatars/" + uniqueFileName;
        
        // Update profile with avatar URL
        profile.setAvatarUrl(avatarUrl);
        profile.setUpdatedAt(LocalDateTime.now());
        mentorProfileRepository.save(profile);
        
        log.info("Avatar uploaded successfully for mentor user ID: {}", userId);
        return avatarUrl;
    }

    private MentorProfileResponse mapToResponse(MentorProfile profile) {
        String fullName = profile.getFullName();
        String firstName = "";
        String lastName = "";
        
        if (fullName != null && fullName.contains(" ")) {
            String[] nameParts = fullName.split(" ", 2);
            firstName = nameParts[0];
            lastName = nameParts.length > 1 ? nameParts[1] : "";
        } else if (fullName != null) {
            firstName = fullName;
        }
        
        MentorProfileResponse.SocialLinks socialLinks = MentorProfileResponse.SocialLinks.builder()
                .linkedin(profile.getLinkedinProfile())
                .github(profile.getGithubProfile())
                .website(profile.getWebsiteUrl())
                .build();
        
        // Parse skills and achievements from text fields (you might want to store these as JSON or separate tables)
        String[] skills = {};
        String[] achievements = {};
        
        if (profile.getMainExpertiseAreas() != null) {
            skills = profile.getMainExpertiseAreas().split(",");
        }
        
        return MentorProfileResponse.builder()
                .id(profile.getUserId())
                .firstName(firstName)
                .lastName(lastName)
                .email(profile.getEmail())
                .bio(profile.getPersonalProfile())
                .specialization(profile.getMainExpertiseAreas())
                .experience(profile.getYearsOfExperience())
                .avatar(profile.getAvatarUrl())
                .socialLinks(socialLinks)
                .skills(skills)
                .achievements(achievements)
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}
