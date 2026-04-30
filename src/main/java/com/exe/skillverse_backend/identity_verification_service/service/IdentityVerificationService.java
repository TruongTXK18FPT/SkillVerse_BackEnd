package com.exe.skillverse_backend.identity_verification_service.service;

import org.springframework.web.multipart.MultipartFile;

public interface IdentityVerificationService {
    
    /**
     * Upload and verify CCCD for an existing mentor.
     * Extracts information using FPT.AI and updates the mentor's profile.
     * @param userId The user ID of the mentor
     * @param cccdFrontFile Front image of the CCCD
     * @param cccdBackFile Back image of the CCCD
     */
    void verifyLegacyMentor(Long userId, MultipartFile cccdFrontFile, MultipartFile cccdBackFile);

    /**
     * Admin approves the CCCD identity verification for a mentor.
     * Sets identityVerified=true and sends email notification.
     * @param userId The mentor's user ID
     * @param adminId The admin's user ID performing the action
     */
    void adminApproveCccd(Long userId, Long adminId);

    /**
     * Mentor cancels their pending CCCD identity verification request.
     * Clears CCCD-related data from the profile.
     * @param userId The user ID of the mentor
     */
    void cancelCccdRequest(Long userId);
}
