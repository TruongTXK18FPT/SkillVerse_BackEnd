package com.exe.skillverse_backend.business_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.service.UserCreationService;
import com.exe.skillverse_backend.business_service.dto.request.BusinessRegistrationRequest;
import com.exe.skillverse_backend.business_service.dto.response.BusinessRegistrationResponse;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import com.exe.skillverse_backend.business_service.service.BusinessRegistrationService;
import com.exe.skillverse_backend.mentor_service.entity.ApplicationStatus;
import com.exe.skillverse_backend.shared.service.CloudinaryService;
import com.exe.skillverse_backend.shared.service.RegistrationService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessRegistrationServiceImpl
                implements BusinessRegistrationService {

        private final UserCreationService userCreationService;
        private final RecruiterProfileRepository recruiterProfileRepository;
        private final CloudinaryService cloudinaryService;
        private final Validator validator;

        @Override
        @Transactional
        public BusinessRegistrationResponse register(BusinessRegistrationRequest request) {
                try {
                        log.info("Starting business/recruiter registration for email: {}", request.getEmail());
                        validateRequest(request);

                        // 1. Create User entity via auth_service
                        User user = userCreationService.createUserForRecruiter(
                                        request.getEmail(),
                                        request.getPassword(),
                                        request.getFullName(),
                                        request.getPhone());

                        // 2. Create RecruiterProfile in business_service
                        createRecruiterProfile(user, request);

                        // 3. OTP is already generated in UserCreationService#createUserForRecruiter
                        // (avoid duplicate send and resend-cooldown errors)
                        log.info("OTP already generated during recruiter user creation: {}", request.getEmail());

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
                                        .otpExpiryMinutes(5)
                                        .nextStep("Check your email for verification code, then wait for admin approval")
                                        .build();

                } catch (Exception e) {
                        log.error("Business registration failed for email: {}", request.getEmail(), e);
                        throw e;
                }
        }

        private void validateRequest(BusinessRegistrationRequest request) {
                Set<ConstraintViolation<BusinessRegistrationRequest>> violations = validator.validate(request);
                if (violations.isEmpty()) {
                        return;
                }

                String errorMessage = violations.stream()
                                .map(ConstraintViolation::getMessage)
                                .distinct()
                                .collect(Collectors.joining("; "));

                throw new IllegalArgumentException(errorMessage);
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
                        MultipartFile companyDocumentsFile,
                        List<MultipartFile> companyDocumentsFiles) {

                try {
                        log.info("Starting business registration for email: {}", email);

                        // 1. Create and populate request object (business logic)
                        BusinessRegistrationRequest request = createBusinessRegistrationRequest(
                                        email, password, confirmPassword, fullName, phone, bio, address, region,
                                        companyName, companyWebsite, companyAddress,
                                        taxCodeOrBusinessRegistrationNumber,
                                        contactPersonPhone, contactPersonPosition, companySize, industry,
                                        companyDocumentsFile,
                                        companyDocumentsFiles);

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
                        MultipartFile companyDocumentsFile,
                        List<MultipartFile> companyDocumentsFiles) {

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

                // Normalize website URL: ensure it starts with http:// or https://
                String normalizedWebsite = companyWebsite;
                if (normalizedWebsite != null && !normalizedWebsite.trim().isEmpty()
                                && !normalizedWebsite.matches("^https?://.*")) {
                        normalizedWebsite = "https://" + normalizedWebsite.trim();
                }
                request.setCompanyWebsite(normalizedWebsite);

                request.setCompanyAddress(companyAddress);
                request.setTaxCodeOrBusinessRegistrationNumber(taxCodeOrBusinessRegistrationNumber);
                request.setContactPersonPhone(contactPersonPhone);
                request.setContactPersonPosition(contactPersonPosition);
                request.setCompanySize(companySize);
                request.setIndustry(industry);

                List<MultipartFile> allFiles = new ArrayList<>();
                if (companyDocumentsFile != null && !companyDocumentsFile.isEmpty()) {
                        allFiles.add(companyDocumentsFile);
                }
                if (companyDocumentsFiles != null && !companyDocumentsFiles.isEmpty()) {
                        for (MultipartFile f : companyDocumentsFiles) {
                                if (f != null && !f.isEmpty())
                                        allFiles.add(f);
                        }
                }

                if (allFiles.isEmpty()) {
                        throw new RuntimeException("Company documents file is required");
                }

                MultipartFile pdfFile = null;
                for (MultipartFile f : allFiles) {
                        String ct = f.getContentType();
                        if (ct != null && ct.equalsIgnoreCase("application/pdf")) {
                                pdfFile = f;
                                break;
                        }
                }

                if (pdfFile == null) {
                        throw new RuntimeException("At least one PDF of business license is required");
                }

                try {
                        log.info("Uploading company documents (PDF) to Cloudinary for: {}", email);
                        var uploadResult = cloudinaryService.uploadFile(pdfFile, "company-documents");
                        String cloudinaryUrl = (String) uploadResult.get("secure_url");
                        request.setCompanyDocumentsUrl(cloudinaryUrl);
                        log.info("Company documents uploaded successfully: {}", cloudinaryUrl);
                } catch (Exception e) {
                        log.error("Failed to upload company documents to Cloudinary for: {}", email, e);
                        throw new RuntimeException("Failed to upload company documents: " + e.getMessage());
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
                                .companyPhone(request.getPhone())
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
