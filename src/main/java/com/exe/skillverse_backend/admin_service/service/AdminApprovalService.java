package com.exe.skillverse_backend.admin_service.service;

import com.exe.skillverse_backend.admin_service.dto.request.ApplicationActionRequest;
import com.exe.skillverse_backend.admin_service.dto.response.AdminApprovalResponse;
import com.exe.skillverse_backend.admin_service.dto.response.ApplicationStatusStatsDto;
import com.exe.skillverse_backend.admin_service.dto.response.ApplicationsResponse;
import com.exe.skillverse_backend.admin_service.dto.response.MentorApplicationDto;
import com.exe.skillverse_backend.admin_service.dto.response.RecruiterApplicationDto;
import com.exe.skillverse_backend.auth_service.entity.*;
import com.exe.skillverse_backend.auth_service.repository.RoleRepository;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import com.exe.skillverse_backend.mentor_service.entity.ApplicationStatus;
import com.exe.skillverse_backend.mentor_service.entity.MentorProfile;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.shared.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AdminApprovalService {

        private final UserRepository userRepository;
        private final MentorProfileRepository mentorProfileRepository;
        private final RecruiterProfileRepository recruiterProfileRepository;
        private final RoleRepository roleRepository;
        private final EmailService emailService;

        public AdminApprovalResponse approveMentor(Long userId, Long adminId) {
                // Find user and mentor profile
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                if (user.getPrimaryRole() != PrimaryRole.MENTOR) {
                        throw new RuntimeException("User is not registered as a mentor");
                }

                // Check if user has verified their email
                if (!user.isEmailVerified()) {
                        throw new RuntimeException("User must verify their email before approval can be processed");
                }

                // Check if user status is INACTIVE (correct status for pending
                // mentor/recruiter)
                if (user.getStatus() != UserStatus.INACTIVE) {
                        throw new RuntimeException(
                                        "User account must be in INACTIVE status (email verified but pending approval)");
                }

                MentorProfile mentorProfile = mentorProfileRepository.findByUserId(userId)
                                .orElseThrow(() -> new RuntimeException("Mentor profile not found"));

                if (mentorProfile.getApplicationStatus() != ApplicationStatus.PENDING) {
                        throw new RuntimeException("Mentor application is not in pending status");
                }

                // Approve the application
                mentorProfile.setApplicationStatus(ApplicationStatus.APPROVED);
                mentorProfile.setApprovalDate(LocalDateTime.now());
                mentorProfile.setApprovedBy(adminId);
                mentorProfileRepository.save(mentorProfile);

                // Activate user account - change from INACTIVE to ACTIVE
                user.setStatus(UserStatus.ACTIVE);

                // Assign MENTOR role
                Role mentorRole = roleRepository.findByName("MENTOR")
                                .orElseThrow(() -> new RuntimeException("MENTOR role not found"));
                user.getRoles().add(mentorRole);

                userRepository.save(user);

                // Send approval email
                emailService.sendApprovalEmail(user.getEmail(), user.getFirstName() + " " + user.getLastName(),
                                "MENTOR");
                return AdminApprovalResponse.builder()
                                .success(true)
                                .message("Mentor application approved successfully")
                                .userEmail(user.getEmail())
                                .userId(userId)
                                .role("MENTOR")
                                .action("APPROVED")
                                .build();
        }

        public AdminApprovalResponse approveRecruiter(Long userId, Long adminId) {
                // Find user and recruiter profile
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                if (user.getPrimaryRole() != PrimaryRole.RECRUITER) {
                        throw new RuntimeException("User is not registered as a recruiter");
                }

                // Check if user has verified their email
                if (!user.isEmailVerified()) {
                        throw new RuntimeException("User must verify their email before approval can be processed");
                }

                // Check if user status is INACTIVE (correct status for pending
                // mentor/recruiter)
                if (user.getStatus() != UserStatus.INACTIVE) {
                        throw new RuntimeException(
                                        "User account must be in INACTIVE status (email verified but pending approval)");
                }

                RecruiterProfile recruiterProfile = recruiterProfileRepository.findByUserId(userId)
                                .orElseThrow(() -> new RuntimeException("Recruiter profile not found"));

                if (recruiterProfile.getApplicationStatus() != ApplicationStatus.PENDING) {
                        throw new RuntimeException("Recruiter application is not in pending status");
                }

                // Approve the application
                recruiterProfile.setApplicationStatus(ApplicationStatus.APPROVED);
                recruiterProfile.setApprovalDate(LocalDateTime.now());
                recruiterProfile.setApprovedBy(adminId);
                recruiterProfileRepository.save(recruiterProfile);

                // Activate user account - change from INACTIVE to ACTIVE
                user.setStatus(UserStatus.ACTIVE);

                // Assign RECRUITER role
                Role recruiterRole = roleRepository.findByName("RECRUITER")
                                .orElseThrow(() -> new RuntimeException("RECRUITER role not found"));
                user.getRoles().add(recruiterRole);

                userRepository.save(user);

                // Send approval email
                emailService.sendApprovalEmail(user.getEmail(), user.getFirstName() + " " + user.getLastName(),
                                "RECRUITER");
                return AdminApprovalResponse.builder()
                                .success(true)
                                .message("Recruiter application approved successfully")
                                .userEmail(user.getEmail())
                                .userId(userId)
                                .role("RECRUITER")
                                .action("APPROVED")
                                .build();
        }

        public AdminApprovalResponse rejectMentor(Long userId, String rejectionReason, Long adminId) {
                // Find user and mentor profile
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                // Check if user has verified their email
                if (!user.isEmailVerified()) {
                        throw new RuntimeException("User must verify their email before rejection can be processed");
                }

                // Check if user status is active (email verified)
                if (user.getStatus() != UserStatus.ACTIVE) {
                        throw new RuntimeException("User account must be active (email verified) before rejection");
                }

                MentorProfile mentorProfile = mentorProfileRepository.findByUserId(userId)
                                .orElseThrow(() -> new RuntimeException("Mentor profile not found"));

                if (mentorProfile.getApplicationStatus() != ApplicationStatus.PENDING) {
                        throw new RuntimeException("Mentor application is not in pending status");
                }

                // Reject the application
                mentorProfile.setApplicationStatus(ApplicationStatus.REJECTED);
                mentorProfile.setRejectionReason(rejectionReason);
                mentorProfile.setApprovedBy(adminId); // Admin who rejected
                mentorProfileRepository.save(mentorProfile);

                // Keep user account status as INACTIVE (rejected applications remain inactive)
                user.setStatus(UserStatus.INACTIVE);
                userRepository.save(user);

                // Send rejection email
                emailService.sendRejectionEmail(user.getEmail(), user.getFirstName() + " " + user.getLastName(),
                                "MENTOR",
                                rejectionReason);
                return AdminApprovalResponse.builder()
                                .success(true)
                                .message("Mentor application rejected")
                                .userEmail(user.getEmail())
                                .userId(userId)
                                .role("MENTOR")
                                .action("REJECTED")
                                .reason(rejectionReason)
                                .build();
        }

        public AdminApprovalResponse rejectRecruiter(Long userId, String rejectionReason, Long adminId) {
                // Find user and recruiter profile
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                // Check if user has verified their email
                if (!user.isEmailVerified()) {
                        throw new RuntimeException("User must verify their email before rejection can be processed");
                }

                // Check if user status is active (email verified)
                if (user.getStatus() != UserStatus.ACTIVE) {
                        throw new RuntimeException("User account must be active (email verified) before rejection");
                }

                RecruiterProfile recruiterProfile = recruiterProfileRepository.findByUserId(userId)
                                .orElseThrow(() -> new RuntimeException("Recruiter profile not found"));

                if (recruiterProfile.getApplicationStatus() != ApplicationStatus.PENDING) {
                        throw new RuntimeException("Recruiter application is not in pending status");
                }

                // Reject the application
                recruiterProfile.setApplicationStatus(ApplicationStatus.REJECTED);
                recruiterProfile.setRejectionReason(rejectionReason);
                recruiterProfile.setApprovedBy(adminId); // Admin who rejected
                recruiterProfileRepository.save(recruiterProfile);

                // Keep user account status as INACTIVE (rejected applications remain inactive)
                user.setStatus(UserStatus.INACTIVE);
                userRepository.save(user);

                // Send rejection email
                emailService.sendRejectionEmail(user.getEmail(), user.getFirstName() + " " + user.getLastName(),
                                "RECRUITER",
                                rejectionReason);
                return AdminApprovalResponse.builder()
                                .success(true)
                                .message("Recruiter application rejected")
                                .userEmail(user.getEmail())
                                .userId(userId)
                                .role("RECRUITER")
                                .action("REJECTED")
                                .reason(rejectionReason)
                                .build();
        }

        // process application
        public AdminApprovalResponse processApplication(ApplicationActionRequest request, Long adminId) {
                log.info("Processing {} action for {} application, userId: {}, adminId: {}",
                                request.getAction(), request.getApplicationType(), request.getUserId(), adminId);

                // Validate rejection reason if action is REJECT
                if ("REJECT".equalsIgnoreCase(request.getAction()) &&
                                (request.getRejectionReason() == null
                                                || request.getRejectionReason().trim().isEmpty())) {
                        throw new IllegalArgumentException("Rejection reason is required for REJECT action");
                }

                // Route to appropriate method based on application type and action
                if ("MENTOR".equalsIgnoreCase(request.getApplicationType())) {
                        if ("APPROVE".equalsIgnoreCase(request.getAction())) {
                                return approveMentor(request.getUserId(), adminId);
                        } else if ("REJECT".equalsIgnoreCase(request.getAction())) {
                                return rejectMentor(request.getUserId(), request.getRejectionReason(), adminId);
                        }
                } else if ("RECRUITER".equalsIgnoreCase(request.getApplicationType())) {
                        if ("APPROVE".equalsIgnoreCase(request.getAction())) {
                                return approveRecruiter(request.getUserId(), adminId);
                        } else if ("REJECT".equalsIgnoreCase(request.getAction())) {
                                return rejectRecruiter(request.getUserId(), request.getRejectionReason(), adminId);
                        }
                }

                throw new IllegalArgumentException("Invalid application type or action: " +
                                request.getApplicationType() + " - " + request.getAction());
        }

        // get application with status filter with pending, approved, rejected, all
        public ApplicationsResponse getApplications(String status) {
                log.info("Fetching applications with status filter: {}", status);

                ApplicationStatus filterStatus = null;
                if (status != null && !status.equalsIgnoreCase("ALL")) {
                        try {
                                filterStatus = ApplicationStatus.valueOf(status.toUpperCase());
                        } catch (IllegalArgumentException e) {
                                throw new IllegalArgumentException("Invalid status: " + status
                                                + ". Valid values: PENDING, APPROVED, REJECTED, ALL");
                        }
                }

                // Get mentor applications
                List<MentorProfile> mentorProfiles;
                if (filterStatus != null) {
                        mentorProfiles = mentorProfileRepository.findByApplicationStatus(filterStatus);
                } else {
                        mentorProfiles = mentorProfileRepository.findAll();
                }

                List<MentorApplicationDto> mentorDtos = mentorProfiles.stream()
                                .map(this::convertToMentorDto)
                                .collect(Collectors.toList());

                // Get recruiter applications
                List<RecruiterProfile> recruiterProfiles;
                if (filterStatus != null) {
                        recruiterProfiles = recruiterProfileRepository.findByApplicationStatus(filterStatus);
                } else {
                        recruiterProfiles = recruiterProfileRepository.findAll();
                }

                List<RecruiterApplicationDto> recruiterDtos = recruiterProfiles.stream()
                                .map(this::convertToRecruiterDto)
                                .collect(Collectors.toList());

                // Calculate statistics
                ApplicationStatusStatsDto stats = calculateStatusStats();

                int totalReturned = mentorDtos.size() + recruiterDtos.size();
                String appliedFilter = status != null ? status.toUpperCase() : "ALL";

                log.info("Found {} mentor applications and {} recruiter applications with filter: {}",
                                mentorDtos.size(), recruiterDtos.size(), appliedFilter);

                return ApplicationsResponse.builder()
                                .mentorApplications(mentorDtos)
                                .recruiterApplications(recruiterDtos)
                                .totalApplications(totalReturned)
                                .filterStatus(appliedFilter)
                                .statusStats(stats)
                                .build();
        }

        /**
         * Calculate application statistics by status
         */
        private ApplicationStatusStatsDto calculateStatusStats() {
                // Get all applications and count by status
                List<MentorProfile> allMentors = mentorProfileRepository.findAll();
                List<RecruiterProfile> allRecruiters = recruiterProfileRepository.findAll();

                // Count by status for mentors
                long mentorPending = allMentors.stream()
                                .filter(m -> m.getApplicationStatus() == ApplicationStatus.PENDING).count();
                long mentorApproved = allMentors.stream()
                                .filter(m -> m.getApplicationStatus() == ApplicationStatus.APPROVED).count();
                long mentorRejected = allMentors.stream()
                                .filter(m -> m.getApplicationStatus() == ApplicationStatus.REJECTED).count();

                // Count by status for recruiters
                long recruiterPending = allRecruiters.stream()
                                .filter(r -> r.getApplicationStatus() == ApplicationStatus.PENDING).count();
                long recruiterApproved = allRecruiters.stream()
                                .filter(r -> r.getApplicationStatus() == ApplicationStatus.APPROVED).count();
                long recruiterRejected = allRecruiters.stream()
                                .filter(r -> r.getApplicationStatus() == ApplicationStatus.REJECTED).count();

                int totalPending = (int) (mentorPending + recruiterPending);
                int totalApproved = (int) (mentorApproved + recruiterApproved);
                int totalRejected = (int) (mentorRejected + recruiterRejected);
                int total = totalPending + totalApproved + totalRejected;

                return ApplicationStatusStatsDto.builder()
                                .pending(totalPending)
                                .approved(totalApproved)
                                .rejected(totalRejected)
                                .total(total)
                                .build();
        }

        /**
         * Convert MentorProfile to DTO for admin review
         */
        private MentorApplicationDto convertToMentorDto(MentorProfile mentor) {
                String plainEmail = mentor.getEmail() != null ? mentor.getEmail() : "N/A";

                // Get user information for email verification status
                User user = mentor.getUser();
                Boolean isEmailVerified = user != null ? user.isEmailVerified() : false;
                String userStatus = user != null ? user.getStatus().name() : "UNKNOWN";

                java.util.List<String> certUrls = null;
                if (mentor.getCertifications() != null && !mentor.getCertifications().isBlank()) {
                        try {
                                certUrls = new com.fasterxml.jackson.databind.ObjectMapper()
                                                .readValue(mentor.getCertifications(),
                                                                new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {
                                                                });
                        } catch (Exception e) {
                                log.warn("Failed to parse certifications JSON for mentor {}", mentor.getUserId(), e);
                        }
                }

                return MentorApplicationDto.builder()
                                .userId(mentor.getUserId())
                                .fullName(mentor.getFullName())
                                .email(plainEmail)
                                .mainExpertiseArea(mentor.getMainExpertiseAreas())
                                .yearsOfExperience(mentor.getYearsOfExperience())
                                .personalProfile(mentor.getPersonalProfile() != null
                                                && mentor.getPersonalProfile().length() > 100
                                                                ? mentor.getPersonalProfile().substring(0, 100) + "..."
                                                                : mentor.getPersonalProfile())
                                .linkedinProfile(mentor.getLinkedinProfile())
                                .cvPortfolioUrl(mentor.getCvPortfolioUrl())
                                .certificatesUrl(mentor.getCertificatesUrl())
                                .certificateUrls(certUrls)
                                .applicationStatus(mentor.getApplicationStatus())
                                .isEmailVerified(isEmailVerified)
                                .userStatus(userStatus)
                                .applicationDate(mentor.getApplicationDate())
                                .approvalDate(mentor.getApprovalDate())
                                .rejectionReason(mentor.getRejectionReason())
                                .build();
        }

        /**
         * Convert RecruiterProfile to DTO for admin review
         */
        private RecruiterApplicationDto convertToRecruiterDto(RecruiterProfile recruiter) {
                // Get user information
                User user = recruiter.getUser();
                Boolean isEmailVerified = user != null ? user.isEmailVerified() : false;
                String userStatus = user != null ? user.getStatus().name() : "UNKNOWN";
                String fullName = user != null ? (user.getFirstName() + " " + user.getLastName()) : "N/A";

                // Use company email from user (now single email approach)
                String companyEmail = user != null ? user.getEmail() : "N/A";

                return RecruiterApplicationDto.builder()
                                .userId(recruiter.getUserId())
                                .fullName(fullName)
                                .email(companyEmail)
                                .companyName(recruiter.getCompanyName() != null ? recruiter.getCompanyName() : "N/A")
                                .companyWebsite(recruiter.getCompanyWebsite() != null ? recruiter.getCompanyWebsite()
                                                : "N/A")
                                .companyAddress(recruiter.getCompanyAddress() != null ? recruiter.getCompanyAddress()
                                                : "N/A")
                                .taxCodeOrBusinessRegistrationNumber(
                                                recruiter.getTaxCodeOrBusinessRegistrationNumber() != null
                                                                ? recruiter.getTaxCodeOrBusinessRegistrationNumber()
                                                                : "N/A")
                                .companyDocumentsUrl(recruiter.getCompanyDocumentsUrl() != null
                                                ? recruiter.getCompanyDocumentsUrl()
                                                : "N/A")
                                // Contact Person Information
                                .contactPersonPhone(recruiter.getContactPersonPhone())
                                .contactPersonPosition(recruiter.getContactPersonPosition() != null
                                                ? recruiter.getContactPersonPosition()
                                                : "N/A")
                                // Company Extended Information
                                .companySize(recruiter.getCompanySize() != null ? recruiter.getCompanySize() : "N/A")
                                .industry(recruiter.getIndustry() != null ? recruiter.getIndustry() : "N/A")
                                // Application Status
                                .applicationStatus(recruiter.getApplicationStatus())
                                .isEmailVerified(isEmailVerified)
                                .userStatus(userStatus)
                                .applicationDate(recruiter.getApplicationDate())
                                .approvalDate(recruiter.getApprovalDate())
                                .rejectionReason(recruiter.getRejectionReason())
                                .build();
        }
}
