package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.service.UserCreationService;
import com.exe.skillverse_backend.business_service.dto.request.BusinessRegistrationRequest;
import com.exe.skillverse_backend.business_service.dto.response.BusinessRegistrationResponse;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import com.exe.skillverse_backend.mentor_service.entity.ApplicationStatus;
import com.exe.skillverse_backend.shared.service.RegistrationService;
import com.exe.skillverse_backend.shared.service.CloudinaryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessRegistrationService
                implements RegistrationService<BusinessRegistrationRequest, BusinessRegistrationResponse> {

        private final UserCreationService userCreationService;
        private final RecruiterProfileRepository recruiterProfileRepository;
        private final CloudinaryService cloudinaryService;

        @Override
        @Transactional
        public BusinessRegistrationResponse register(BusinessRegistrationRequest request) {
                try {
                        log.info("Starting business/recruiter registration for email: {}", request.getEmail());

                        // 1. Create User entity via auth_service
                        User user = userCreationService.createUserForRecruiter(
                                        request.getEmail(),
                                        request.getPassword(),
                                        request.getFullName());

                        // 2. Create RecruiterProfile in business_service
                        createRecruiterProfile(user, request);

                        // 3. Generate OTP for email verification (only after successful profile
                        // creation)
                        userCreationService.generateOtpForUser(request.getEmail());
                        log.info("Generated OTP for recruiter user: {}", request.getEmail());

                        // 4. Log successful registration
                        return BusinessRegistrationResponse.builder()
                                        .success(true)
                                        .message("Business registration successful! Your application is pending admin approval.")
                                        .email(request.getEmail())
                                        .userId(user.getId())
                                        .recruiterProfileId(user.getId()) // RecruiterProfile uses userId as primary key
                                        .applicationStatus(ApplicationStatus.PENDING.name())
                                        .role("RECRUITER")
                                        .companyName(request.getCompanyName())
                                        .requiresVerification(true)
                                        .otpExpiryMinutes(10)
                                        .nextStep("Check your email for verification code, then wait for admin approval")
                                        .build();

                } catch (Exception e) {
                        log.error("Business registration failed for email: {}", request.getEmail(), e);
                        throw e;
                }
        }

        /**
         * Register business/recruiter with individual parameters (called from
         * controller)
         */
        @Transactional
        public BusinessRegistrationResponse registerBusiness(
                        String email,
                        String password,
                        String confirmPassword,
                        String fullName,
                        String phone,
                        String bio,
                        String address,
                        String region,
                        String companyName,
                        String companyWebsite,
                        String companyAddress,
                        String taxCodeOrBusinessRegistrationNumber,
                        String contactPersonPhone,
                        String contactPersonPosition,
                        String companySize,
                        String industry,
                        MultipartFile companyDocumentsFile) {

                try {
                        log.info("Starting business registration for email: {}", email);

                        // 1. Create and populate request object (business logic)
                        BusinessRegistrationRequest request = createBusinessRegistrationRequest(
                                        email, password, confirmPassword, fullName, phone, bio, address, region,
                                        companyName, companyWebsite, companyAddress,
                                        taxCodeOrBusinessRegistrationNumber,
                                        contactPersonPhone, contactPersonPosition, companySize, industry,
                                        companyDocumentsFile);

                        // 2. Process registration using existing logic
                        return register(request);

                } catch (Exception e) {
                        log.error("Business registration failed for email: {}", email, e);
                        throw e;
                }
        }

        /**
         * Create BusinessRegistrationRequest from individual parameters (business
         * logic)
         */
        private BusinessRegistrationRequest createBusinessRegistrationRequest(
                        String email,
                        String password,
                        String confirmPassword,
                        String fullName,
                        String phone,
                        String bio,
                        String address,
                        String region,
                        String companyName,
                        String companyWebsite,
                        String companyAddress,
                        String taxCodeOrBusinessRegistrationNumber,
                        String contactPersonPhone,
                        String contactPersonPosition,
                        String companySize,
                        String industry,
                        MultipartFile companyDocumentsFile) {

                BusinessRegistrationRequest request = new BusinessRegistrationRequest();
                request.setEmail(email);
                request.setPassword(password);
                request.setConfirmPassword(confirmPassword);
                request.setFullName(fullName);
                request.setPhone(phone);
                request.setBio(bio);
                request.setAddress(address);
                request.setRegion(region);
                request.setCompanyName(companyName);
                request.setCompanyWebsite(companyWebsite);
                request.setCompanyAddress(companyAddress);
                request.setTaxCodeOrBusinessRegistrationNumber(taxCodeOrBusinessRegistrationNumber);
                request.setContactPersonPhone(contactPersonPhone);
                request.setContactPersonPosition(contactPersonPosition);
                request.setCompanySize(companySize);
                request.setIndustry(industry);

                // Handle file upload using Cloudinary
                if (companyDocumentsFile != null && !companyDocumentsFile.isEmpty()) {
                        try {
                                log.info("Uploading company documents to Cloudinary for: {}", email);
                                var uploadResult = cloudinaryService.uploadFile(companyDocumentsFile,
                                                "company-documents");
                                String cloudinaryUrl = (String) uploadResult.get("secure_url");
                                request.setCompanyDocumentsUrl(cloudinaryUrl);
                                log.info("Company documents uploaded successfully: {}", cloudinaryUrl);
                        } catch (Exception e) {
                                log.error("Failed to upload company documents to Cloudinary for: {}", email, e);
                                throw new RuntimeException("Failed to upload company documents: " + e.getMessage());
                        }
                } else {
                        throw new RuntimeException("Company documents file is required");
                }

                return request;
        }

        /**
         * Create RecruiterProfile with application pending status
         */
        private void createRecruiterProfile(User user, BusinessRegistrationRequest request) {
                RecruiterProfile recruiterProfile = RecruiterProfile.builder()
                                .user(user) // Set the User entity reference for @MapsId (userId will be auto-derived)
                                .companyName(request.getCompanyName())
                                .companyWebsite(request.getCompanyWebsite())
                                .companyAddress(request.getCompanyAddress())
                                .taxCodeOrBusinessRegistrationNumber(request.getTaxCodeOrBusinessRegistrationNumber())
                                .companyDocumentsUrl(request.getCompanyDocumentsUrl())
                                // Contact Person Information
                                .contactPersonPhone(request.getContactPersonPhone())
                                .contactPersonPosition(request.getContactPersonPosition())
                                // Company Extended Information
                                .companySize(request.getCompanySize())
                                .industry(request.getIndustry())
                                // Application Status
                                .applicationStatus(ApplicationStatus.PENDING)
                                .applicationDate(LocalDateTime.now())
                                .build();

                recruiterProfileRepository.save(recruiterProfile);
                log.info("Created recruiter profile for user: {} with company: {}, position: {}, size: {}, industry: {}",
                                user.getId(), request.getCompanyName(), request.getContactPersonPosition(),
                                request.getCompanySize(), request.getIndustry());
        }
}