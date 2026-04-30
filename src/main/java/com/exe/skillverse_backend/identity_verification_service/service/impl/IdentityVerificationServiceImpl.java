package com.exe.skillverse_backend.identity_verification_service.service.impl;

import com.exe.skillverse_backend.identity_verification_service.dto.IdCardExtractionResult;
import com.exe.skillverse_backend.identity_verification_service.service.FptAiEkycService;
import com.exe.skillverse_backend.identity_verification_service.service.IdentityVerificationService;
import com.exe.skillverse_backend.mentor_service.entity.MentorProfile;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.shared.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdentityVerificationServiceImpl implements IdentityVerificationService {

    private final FptAiEkycService fptAiEkycService;
    private final MentorProfileRepository mentorProfileRepository;
    private final ObjectMapper objectMapper;
    private final EmailService emailService;
    private final UserRepository userRepository;


    @Override
    @Transactional
    public void verifyLegacyMentor(Long userId, MultipartFile cccdFrontFile, MultipartFile cccdBackFile) {
        log.info("Processing legacy mentor identity verification for userId: {}", userId);
        
        MentorProfile mentorProfile = mentorProfileRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Mentor profile not found for user: " + userId));

        if (Boolean.TRUE.equals(mentorProfile.getIdentityVerified())) {
            throw new IllegalArgumentException("Mentor already verified.");
        }

        if (cccdFrontFile == null || cccdFrontFile.isEmpty() || cccdBackFile == null || cccdBackFile.isEmpty()) {
            throw new IllegalArgumentException("Both front and back CCCD images are required.");
        }

        // We do NOT save images to Cloudinary or anywhere else due to privacy policy.
        // We only extract data using FPT.AI and then discard the images.
        
        byte[] frontBytes;
        byte[] backBytes;
        String frontName;
        String backName;
        
        try {
            frontBytes = cccdFrontFile.getBytes();
            backBytes = cccdBackFile.getBytes();
            frontName = cccdFrontFile.getOriginalFilename();
            backName = cccdBackFile.getOriginalFilename();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read CCCD images: " + e.getMessage());
        }

        // Set initial state
        mentorProfile.setIdentityVerified(false);
        mentorProfile.setCccdExtractedData("{\"status\":\"processing\"}");
        mentorProfileRepository.save(mentorProfile);
        
        log.info("Successfully accepted identity verification request for userId: {}. Processing async...", userId);

        // Process FPT.AI in background
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                log.info("Async FPT.AI extraction started for userId: {}", userId);
                IdCardExtractionResult frontResult = fptAiEkycService.extractIdCardInfo(frontBytes, frontName);
                IdCardExtractionResult backResult = fptAiEkycService.extractIdCardInfo(backBytes, backName);

                MentorProfile profile = mentorProfileRepository.findById(userId).orElse(null);
                if (profile == null) return;

                if (!frontResult.isSuccess() || !backResult.isSuccess()) {
                    log.error("FPT.AI extraction failed for userId: {}. Front: {}, Back: {}", 
                            userId, frontResult.getErrorMessage(), backResult.getErrorMessage());
                    profile.setCccdExtractedData("{\"status\":\"error\", \"message\":\"Extraction failed\"}");
                    mentorProfileRepository.save(profile);
                    return;
                }

                // Combine extraction results into one JSON
                Map<String, Object> combinedData = new HashMap<>();
                combinedData.put("front", parseJsonQuietly(frontResult.getRawJson()));
                combinedData.put("back", parseJsonQuietly(backResult.getRawJson()));
                
                String extractedJson = "{}";
                try {
                    extractedJson = objectMapper.writeValueAsString(combinedData);
                } catch (Exception e) {
                    log.warn("Failed to serialize combined FPT.AI data", e);
                }

                profile.setCccdNumber(frontResult.getIdNumber());
                profile.setCccdFullName(frontResult.getFullName());
                profile.setCccdDob(frontResult.getDob());
                profile.setCccdExtractedData(extractedJson);
                // Do not set cccdFrontUrl and cccdBackUrl as they are not stored
                
                mentorProfileRepository.save(profile);
                log.info("Async FPT.AI extraction completed for userId: {}", userId);
            } catch (Exception e) {
                log.error("Async FPT.AI extraction threw exception for userId: {}", userId, e);
            }
        });
    }
    
    private Object parseJsonQuietly(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return json;
        }
    }
    
    private String slugify(String input) {
        if (input == null) return "unknown";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        normalized = normalized.replaceAll("[^a-zA-Z0-9]+", "_");
        normalized = normalized.replaceAll("_+", "_");
        normalized = normalized.replaceAll("^_|_$", "");
        return normalized;
    }

    @Override
    @Transactional
    public void adminApproveCccd(Long userId, Long adminId) {
        log.info("Admin {} approving CCCD identity verification for userId: {}", adminId, userId);

        MentorProfile mentorProfile = mentorProfileRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Mentor profile not found for user: " + userId));

        if (Boolean.TRUE.equals(mentorProfile.getIdentityVerified())) {
            throw new IllegalArgumentException("Mentor CCCD is already verified.");
        }

        mentorProfile.setIdentityVerified(true);
        mentorProfileRepository.save(mentorProfile);

        // Send email notification to mentor
        try {
            var user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                emailService.sendCccdVerificationApprovedEmail(user.getEmail(), user.getFullName());
            }
        } catch (Exception e) {
            log.error("Failed to send CCCD approval email for userId: {}", userId, e);
        }

        log.info("Admin {} successfully approved CCCD for userId: {}", adminId, userId);
    }
}
