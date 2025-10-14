package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.dto.request.RecruiterProfileUpdateRequest;
import com.exe.skillverse_backend.business_service.dto.response.RecruiterProfileResponse;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecruiterProfileService {

    private final RecruiterProfileRepository recruiterProfileRepository;
    private final UserRepository userRepository;

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

    private RecruiterProfileResponse mapToResponse(RecruiterProfile profile, User user) {
        return RecruiterProfileResponse.builder()
                .userId(profile.getUserId())
                .email(user.getEmail())
                .companyName(profile.getCompanyName())
                .companyWebsite(profile.getCompanyWebsite())
                .companyAddress(profile.getCompanyAddress())
                .taxCodeOrBusinessRegistrationNumber(profile.getTaxCodeOrBusinessRegistrationNumber())
                .companyDocumentsUrl(profile.getCompanyDocumentsUrl())
                .applicationStatus(profile.getApplicationStatus())
                .applicationDate(profile.getApplicationDate())
                .approvalDate(profile.getApprovalDate())
                .rejectionReason(profile.getRejectionReason())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}
