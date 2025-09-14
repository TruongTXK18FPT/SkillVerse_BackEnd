package com.exe.skillverse_backend.admin_service.service;

import com.exe.skillverse_backend.admin_service.dto.response.AdminApprovalResponse;
import com.exe.skillverse_backend.auth_service.entity.*;
import com.exe.skillverse_backend.auth_service.repository.RoleRepository;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import com.exe.skillverse_backend.mentor_service.entity.ApplicationStatus;
import com.exe.skillverse_backend.mentor_service.entity.MentorProfile;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.shared.service.AuditService;
import com.exe.skillverse_backend.shared.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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
    private final AuditService auditService;

    public List<MentorProfile> getPendingMentorApplications() {
        return mentorProfileRepository.findByApplicationStatus(ApplicationStatus.PENDING);
    }

    public List<RecruiterProfile> getPendingRecruiterApplications() {
        return recruiterProfileRepository.findByApplicationStatus(ApplicationStatus.PENDING);
    }

    public AdminApprovalResponse approveMentor(Long userId) {
        // Get current admin user ID for audit
        Long adminId = getCurrentAdminId();

        // Find user and mentor profile
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getPrimaryRole() != PrimaryRole.MENTOR) {
            throw new RuntimeException("User is not registered as a mentor");
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

        // Activate user account
        user.setAccountStatus(AccountStatus.ACTIVE);

        // Assign MENTOR role
        Role mentorRole = roleRepository.findByName("MENTOR")
                .orElseThrow(() -> new RuntimeException("MENTOR role not found"));
        user.getRoles().add(mentorRole);

        userRepository.save(user);

        // Send approval email
        emailService.sendApprovalEmail(user.getEmail(), user.getFirstName() + " " + user.getLastName(), "MENTOR");

        // Log the approval
        auditService.logAction(adminId, "MENTOR_APPROVED", "USER", userId.toString(),
                "Mentor application approved for user: " + user.getEmail());

        return AdminApprovalResponse.builder()
                .success(true)
                .message("Mentor application approved successfully")
                .userEmail(user.getEmail())
                .userId(userId)
                .role("MENTOR")
                .action("APPROVED")
                .build();
    }

    public AdminApprovalResponse approveRecruiter(Long userId) {
        // Get current admin user ID for audit
        Long adminId = getCurrentAdminId();

        // Find user and recruiter profile
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getPrimaryRole() != PrimaryRole.RECRUITER) {
            throw new RuntimeException("User is not registered as a recruiter");
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

        // Activate user account
        user.setAccountStatus(AccountStatus.ACTIVE);

        // Assign RECRUITER role
        Role recruiterRole = roleRepository.findByName("RECRUITER")
                .orElseThrow(() -> new RuntimeException("RECRUITER role not found"));
        user.getRoles().add(recruiterRole);

        userRepository.save(user);

        // Send approval email
        emailService.sendApprovalEmail(user.getEmail(), user.getFirstName() + " " + user.getLastName(), "RECRUITER");

        // Log the approval
        auditService.logAction(adminId, "RECRUITER_APPROVED", "USER", userId.toString(),
                "Recruiter application approved for user: " + user.getEmail());

        return AdminApprovalResponse.builder()
                .success(true)
                .message("Recruiter application approved successfully")
                .userEmail(user.getEmail())
                .userId(userId)
                .role("RECRUITER")
                .action("APPROVED")
                .build();
    }

    public AdminApprovalResponse rejectMentor(Long userId, String rejectionReason) {
        // Get current admin user ID for audit
        Long adminId = getCurrentAdminId();

        // Find user and mentor profile
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

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

        // Set user account status to rejected
        user.setAccountStatus(AccountStatus.REJECTED);
        userRepository.save(user);

        // Send rejection email
        emailService.sendRejectionEmail(user.getEmail(), user.getFirstName() + " " + user.getLastName(), "MENTOR",
                rejectionReason);

        // Log the rejection
        auditService.logAction(adminId, "MENTOR_REJECTED", "USER", userId.toString(),
                "Mentor application rejected for user: " + user.getEmail() + ", reason: " + rejectionReason);

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

    public AdminApprovalResponse rejectRecruiter(Long userId, String rejectionReason) {
        // Get current admin user ID for audit
        Long adminId = getCurrentAdminId();

        // Find user and recruiter profile
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

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

        // Set user account status to rejected
        user.setAccountStatus(AccountStatus.REJECTED);
        userRepository.save(user);

        // Send rejection email
        emailService.sendRejectionEmail(user.getEmail(), user.getFirstName() + " " + user.getLastName(), "RECRUITER",
                rejectionReason);

        // Log the rejection
        auditService.logAction(adminId, "RECRUITER_REJECTED", "USER", userId.toString(),
                "Recruiter application rejected for user: " + user.getEmail() + ", reason: " + rejectionReason);

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

    private Long getCurrentAdminId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof String) {
            String email = (String) authentication.getPrincipal();
            User admin = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Admin user not found"));
            return admin.getId();
        }
        throw new RuntimeException("No authenticated admin found");
    }
}