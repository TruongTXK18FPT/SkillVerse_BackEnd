package com.exe.skillverse_backend.business_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.dto.request.RecruiterProfileUpdateRequest;
import com.exe.skillverse_backend.business_service.dto.response.RecruiterProfileResponse;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import com.exe.skillverse_backend.business_service.service.RecruiterProfileService;
import com.exe.skillverse_backend.shared.service.CloudinaryService;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecruiterProfileServiceImpl implements RecruiterProfileService {

    private final RecruiterProfileRepository recruiterProfileRepository;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;

    @Transactional(readOnly = true)
    public RecruiterProfileResponse getRecruiterProfile(Long userId) {
        log.info("Getting recruiter profile for user ID: {}", userId);

        RecruiterProfile profile = recruiterProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Recruiter profile not found for user ID: " + userId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return mapToResponse(profile, user);
    }

    @Transactional
    public RecruiterProfileResponse updateRecruiterProfile(Long userId, RecruiterProfileUpdateRequest request) {
        log.info("Updating recruiter profile for user ID: {}", userId);

        RecruiterProfile profile = recruiterProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Recruiter profile not found for user ID: " + userId));

        // Update fields
        profile.setCompanyName(request.getCompanyName());
        profile.setCompanyWebsite(request.getCompanyWebsite());
        profile.setCompanyAddress(request.getCompanyAddress());
        profile.setTaxCodeOrBusinessRegistrationNumber(request.getTaxCodeOrBusinessRegistrationNumber());

        if (request.getCompanyDocumentsUrl() != null && !request.getCompanyDocumentsUrl().isEmpty()) {
            profile.setCompanyDocumentsUrl(request.getCompanyDocumentsUrl());
        }

        RecruiterProfile savedProfile = recruiterProfileRepository.save(profile);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        log.info("Recruiter profile updated successfully for user ID: {}", userId);
        return mapToResponse(savedProfile, user);
    }

    @Override
    @Transactional
    public String uploadCompanyLogo(Long userId, MultipartFile file) {
        log.info("Uploading company logo for recruiter user ID: {}", userId);

        RecruiterProfile profile = recruiterProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Recruiter profile not found for user ID: " + userId));

        try {
            Map<String, Object> uploadResult = cloudinaryService.uploadImage(file, "recruiters/company-logos");
            String companyLogoUrl = (String) uploadResult.get("secure_url");
            String companyLogoPublicId = (String) uploadResult.get("public_id");

            String oldPublicId = profile.getCompanyLogoPublicId();
            if (oldPublicId != null && !oldPublicId.isBlank()) {
                try {
                    cloudinaryService.deleteFile(oldPublicId, "image");
                } catch (Exception deleteEx) {
                    log.warn("Failed to delete old company logo {}: {}", oldPublicId, deleteEx.getMessage());
                }
            }

            profile.setCompanyLogoUrl(companyLogoUrl);
            profile.setCompanyLogoPublicId(companyLogoPublicId);
            recruiterProfileRepository.save(profile);

            log.info("Company logo uploaded successfully for recruiter user ID: {}", userId);
            return companyLogoUrl;
        } catch (IOException e) {
            log.error("Failed to upload company logo for recruiter user ID: {}", userId, e);
            throw new RuntimeException("Failed to upload company logo: " + e.getMessage(), e);
        }
    }

    private RecruiterProfileResponse mapToResponse(RecruiterProfile profile, User user) {
        return RecruiterProfileResponse.builder()
                .userId(profile.getUserId())
                .email(user.getEmail())
                .companyName(profile.getCompanyName())
                .companyWebsite(profile.getCompanyWebsite())
                .companyAddress(profile.getCompanyAddress())
                .companyPhone(profile.getCompanyPhone())
                .companyLogoUrl(profile.getCompanyLogoUrl())
                .taxCodeOrBusinessRegistrationNumber(profile.getTaxCodeOrBusinessRegistrationNumber())
                .companyDocumentsUrl(profile.getCompanyDocumentsUrl())
                .contactPersonPhone(profile.getContactPersonPhone())
                .contactPersonPosition(profile.getContactPersonPosition())
                .companySize(profile.getCompanySize())
                .industry(profile.getIndustry())
                .applicationStatus(profile.getApplicationStatus())
                .applicationDate(profile.getApplicationDate())
                .approvalDate(profile.getApprovalDate())
                .rejectionReason(profile.getRejectionReason())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}
