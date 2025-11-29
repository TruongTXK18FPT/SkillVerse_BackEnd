package com.exe.skillverse_backend.mentor_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.service.UserCreationService;
import com.exe.skillverse_backend.mentor_service.dto.request.MentorRegistrationRequest;
import com.exe.skillverse_backend.mentor_service.dto.response.MentorRegistrationResponse;
import com.exe.skillverse_backend.mentor_service.entity.ApplicationStatus;
import com.exe.skillverse_backend.mentor_service.entity.MentorProfile;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
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
public class MentorRegistrationService
                implements RegistrationService<MentorRegistrationRequest, MentorRegistrationResponse> {

        private final UserCreationService userCreationService;
        private final MentorProfileRepository mentorProfileRepository;
        private final CloudinaryService cloudinaryService;

        @Override
        @Transactional
        public MentorRegistrationResponse register(MentorRegistrationRequest request) {
                try {
                        log.info("Starting mentor registration for email: {}", request.getEmail());

                        // 1. Create User entity via auth_service
                        User user = userCreationService.createUserForMentor(
                                        request.getEmail(),
                                        request.getPassword(),
                                        request.getFullName());

                        // 2. Create MentorProfile in mentor_service
                        createMentorProfile(user, request);

                        // 3. Generate OTP for email verification (only after successful profile
                        // creation)
                        userCreationService.generateOtpForUser(request.getEmail());
                        log.info("Generated OTP for mentor user: {}", request.getEmail());

                        // 4. Log successful registration
                        return MentorRegistrationResponse.builder()
                                        .success(true)
                                        .message("Mentor registration successful! Your application is pending admin approval.")
                                        .email(request.getEmail())
                                        .userId(user.getId())
                                        .mentorProfileId(user.getId()) // MentorProfile uses userId as primary key
                                        .applicationStatus(ApplicationStatus.PENDING.name())
                                        .role("MENTOR")
                                        .requiresVerification(true)
                                        .otpExpiryMinutes(5)
                                        .nextStep("Check your email for verification code, then wait for admin approval")
                                        .build();

                } catch (Exception e) {
                        log.error("Mentor registration failed for email: {}", request.getEmail(), e);
                        throw e;
                }
        }

        /**
         * Register mentor with individual parameters (called from controller)
         */
        @Transactional
        public MentorRegistrationResponse registerMentor(
                        String email,
                        String password,
                        String confirmPassword,
                        String fullName,
                        String phone,
                        String bio,
                        String address,
                        String region,
                        String linkedinProfile,
                        String mainExpertiseArea,
                        Integer yearsOfExperience,
                        String personalProfile,
                        MultipartFile cvPortfolioFile,
                        MultipartFile certificatesFile,
                        MultipartFile[] certificatesFiles,
                        Boolean mergeCertificates) {

                try {
                        log.info("Starting mentor registration for email: {}", email);

                        // 1. Create and populate request object (business logic)
                        MentorRegistrationRequest request = createMentorRegistrationRequest(
                                        email, password, confirmPassword, fullName, phone, bio, address, region,
                                        linkedinProfile, mainExpertiseArea, yearsOfExperience, personalProfile,
                                        cvPortfolioFile, certificatesFile, certificatesFiles, mergeCertificates);

                        // 2. Process registration using existing logic
                        return register(request);

                } catch (Exception e) {
                        log.error("Mentor registration failed for email: {}", email, e);
                        throw e;
                }
        }

        /**
         * Create MentorRegistrationRequest from individual parameters (business logic)
         */
        private MentorRegistrationRequest createMentorRegistrationRequest(
                        String email,
                        String password,
                        String confirmPassword,
                        String fullName,
                        String phone,
                        String bio,
                        String address,
                        String region,
                        String linkedinProfile,
                        String mainExpertiseArea,
                        Integer yearsOfExperience,
                        String personalProfile,
                        MultipartFile cvPortfolioFile,
                        MultipartFile certificatesFile,
                        MultipartFile[] certificatesFiles,
                        Boolean mergeCertificates) {

                MentorRegistrationRequest request = new MentorRegistrationRequest();
                request.setEmail(email);
                request.setPassword(password);
                request.setConfirmPassword(confirmPassword);
                request.setFullName(fullName);
                request.setPhone(phone);
                request.setBio(bio);
                request.setAddress(address);
                request.setRegion(region);
                request.setLinkedinProfile(linkedinProfile);
                request.setMainExpertiseArea(mainExpertiseArea);
                request.setYearsOfExperience(yearsOfExperience);
                request.setPersonalProfile(personalProfile);

                if (cvPortfolioFile != null && !cvPortfolioFile.isEmpty()) {
                        try {
                                log.info("Uploading mentor CV/Portfolio to Cloudinary for: {}", email);
                                String nameSlug = slugify(fullName);
                                String timestamp = java.time.LocalDateTime.now()
                                                .format(java.time.format.DateTimeFormatter
                                                                .ofPattern("yyyyMMdd_HHmmss"));
                                String publicId = "CV_Portfolio_" + nameSlug + "_" + timestamp;
                                var uploadResult = cloudinaryService.uploadFileNamed(cvPortfolioFile, "mentor-cv",
                                                publicId);
                                String cloudinaryUrl = (String) uploadResult.get("secure_url");
                                request.setCvPortfolioUrl(cloudinaryUrl);
                                log.info("CV/Portfolio uploaded successfully: {}", cloudinaryUrl);
                        } catch (Exception e) {
                                log.error("Failed to upload mentor CV/Portfolio for: {}", email, e);
                                throw new RuntimeException("Failed to upload CV/Portfolio: " + e.getMessage());
                        }
                }

                // Prefer array if provided; fallback to single file
                if (certificatesFiles != null && certificatesFiles.length > 0) {
                        try {
                                log.info("Uploading {} mentor certificate files for: {}", certificatesFiles.length,
                                                email);
                                String nameSlug = slugify(fullName);
                                String timestamp = java.time.LocalDateTime.now()
                                                .format(java.time.format.DateTimeFormatter
                                                                .ofPattern("yyyyMMdd_HHmmss"));
                                String basePublicId = "ChungChi_" + nameSlug + "_" + timestamp;

                                boolean shouldMerge = mergeCertificates != null ? mergeCertificates
                                                : certificatesFiles.length > 3;

                                java.util.List<String> uploadedUrls = new java.util.ArrayList<>();
                                for (MultipartFile cf : certificatesFiles) {
                                        if (cf == null || cf.isEmpty())
                                                continue;
                                        String subId = basePublicId + "_" + (uploadedUrls.size() + 1);
                                        var r = cloudinaryService.uploadFileNamed(cf, "mentor-certificates", subId);
                                        uploadedUrls.add((String) r.get("secure_url"));
                                }

                                if (shouldMerge && !uploadedUrls.isEmpty()) {
                                        byte[] combined = combineCertificatesToPdf(certificatesFiles);
                                        String mergedId = basePublicId + "_MERGED";
                                        // Wrap bytes as MultipartFile with application/pdf
                                        MultipartFile pdfFile = new org.springframework.web.multipart.MultipartFile() {
                                                @Override
                                                public String getName() {
                                                        return "combined";
                                                }

                                                @Override
                                                public String getOriginalFilename() {
                                                        return "combined.pdf";
                                                }

                                                @Override
                                                public String getContentType() {
                                                        return "application/pdf";
                                                }

                                                @Override
                                                public boolean isEmpty() {
                                                        return combined.length == 0;
                                                }

                                                @Override
                                                public long getSize() {
                                                        return combined.length;
                                                }

                                                @Override
                                                public byte[] getBytes() {
                                                        return combined;
                                                }

                                                @Override
                                                public java.io.InputStream getInputStream() {
                                                        return new java.io.ByteArrayInputStream(combined);
                                                }

                                                @Override
                                                public void transferTo(java.io.File dest) throws java.io.IOException {
                                                        try (var out = new java.io.FileOutputStream(dest)) {
                                                                out.write(combined);
                                                        }
                                                }
                                        };
                                        var uploadResult = cloudinaryService.uploadFileNamed(pdfFile,
                                                        "mentor-certificates", mergedId);
                                        String mergedUrl = (String) uploadResult.get("secure_url");
                                        request.setCertificatesUrl(mergedUrl);
                                } else if (!uploadedUrls.isEmpty()) {
                                        request.setCertificatesUrl(uploadedUrls.get(0));
                                }

                                request.setCertificateUrls(uploadedUrls);
                                log.info("Certificates uploaded ({} files). Merge? {}", uploadedUrls.size(),
                                                shouldMerge);
                        } catch (Exception e) {
                                log.error("Failed to upload mentor certificates array for: {}", email, e);
                                throw new RuntimeException("Failed to upload certificates: " + e.getMessage());
                        }
                } else if (certificatesFile != null && !certificatesFile.isEmpty()) {
                        try {
                                log.info("Uploading mentor certificates to Cloudinary for: {}", email);
                                String nameSlug = slugify(fullName);
                                String timestamp = java.time.LocalDateTime.now()
                                                .format(java.time.format.DateTimeFormatter
                                                                .ofPattern("yyyyMMdd_HHmmss"));
                                String publicId = "ChungChi_" + nameSlug + "_" + timestamp;
                                var uploadResult = cloudinaryService.uploadFileNamed(certificatesFile,
                                                "mentor-certificates", publicId);
                                String cloudinaryUrl = (String) uploadResult.get("secure_url");
                                request.setCertificatesUrl(cloudinaryUrl);
                                log.info("Certificates uploaded successfully: {}", cloudinaryUrl);
                        } catch (Exception e) {
                                log.error("Failed to upload mentor certificates for: {}", email, e);
                                throw new RuntimeException("Failed to upload certificates: " + e.getMessage());
                        }
                }

                return request;
        }

        private String slugify(String input) {
                if (input == null)
                        return "unknown";
                String normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
                                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
                normalized = normalized.replaceAll("[^a-zA-Z0-9]+", "_");
                normalized = normalized.replaceAll("_+", "_");
                normalized = normalized.replaceAll("^_|_$", "");
                return normalized;
        }

        /**
         * Create MentorProfile with new form fields and application pending status
         */
        private void createMentorProfile(User user, MentorRegistrationRequest request) {
                MentorProfile mentorProfile = MentorProfile.builder()
                                .user(user) // Set the User entity reference for @MapsId (userId will be auto-derived)
                                // New form fields
                                .fullName(request.getFullName())
                                .email(request.getEmail())
                                .linkedinProfile(request.getLinkedinProfile())
                                .mainExpertiseAreas(request.getMainExpertiseArea())
                                .yearsOfExperience(request.getYearsOfExperience())
                                .personalProfile(request.getPersonalProfile())
                                .cvPortfolioUrl(request.getCvPortfolioUrl())
                                .certificatesUrl(request.getCertificatesUrl())
                                .certifications(request.getCertificateUrls() != null
                                                ? toJson(request.getCertificateUrls())
                                                : null)
                                // Application status
                                .applicationStatus(ApplicationStatus.PENDING)
                                .applicationDate(LocalDateTime.now())
                                .build();

                mentorProfileRepository.save(mentorProfile);
                log.info("Created mentor profile for user: {} with full name: {}", user.getId(), request.getFullName());
        }

        private String toJson(java.util.List<String> urls) {
                try {
                        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(urls);
                } catch (Exception e) {
                        log.warn("Failed to serialize certificate URLs", e);
                        return null;
                }
        }

        private byte[] combineCertificatesToPdf(MultipartFile[] files) throws Exception {
                java.util.List<MultipartFile> list = new java.util.ArrayList<>();
                for (MultipartFile f : files) {
                        if (f != null && !f.isEmpty())
                                list.add(f);
                }
                if (list.isEmpty())
                        return new byte[0];

                java.util.List<MultipartFile> pdfs = new java.util.ArrayList<>();
                java.util.List<MultipartFile> images = new java.util.ArrayList<>();
                for (MultipartFile f : list) {
                        String ct = f.getContentType();
                        boolean isPdf = ct != null && (ct.equalsIgnoreCase("application/pdf")
                                        || ct.equalsIgnoreCase("application/x-pdf")
                                        || ct.equalsIgnoreCase("application/acrobat"));
                        boolean isImg = ct != null && ct.startsWith("image/");
                        if (isPdf)
                                pdfs.add(f);
                        else if (isImg)
                                images.add(f);
                }

                byte[] imagesPdfBytes = null;
                if (!images.isEmpty()) {
                        org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument();
                        for (MultipartFile img : images) {
                                java.awt.image.BufferedImage bi = javax.imageio.ImageIO.read(img.getInputStream());
                                if (bi == null)
                                        continue;
                                org.apache.pdfbox.pdmodel.common.PDRectangle pageSize = org.apache.pdfbox.pdmodel.common.PDRectangle.A4;
                                org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage(pageSize);
                                doc.addPage(page);
                                org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject pdImage = org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
                                                .createFromImage(doc, bi);
                                float margin = 36f;
                                float maxW = pageSize.getWidth() - 2 * margin;
                                float maxH = pageSize.getHeight() - 2 * margin;
                                float imgW = pdImage.getWidth();
                                float imgH = pdImage.getHeight();
                                float scale = Math.min(maxW / imgW, maxH / imgH);
                                float drawW = imgW * scale;
                                float drawH = imgH * scale;
                                float x = (pageSize.getWidth() - drawW) / 2f;
                                float y = (pageSize.getHeight() - drawH) / 2f;
                                try (org.apache.pdfbox.pdmodel.PDPageContentStream cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(
                                                doc, page)) {
                                        cs.drawImage(pdImage, x, y, drawW, drawH);
                                }
                        }
                        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
                                doc.save(baos);
                                imagesPdfBytes = baos.toByteArray();
                        } finally {
                                doc.close();
                        }
                }

                if (pdfs.isEmpty()) {
                        return imagesPdfBytes != null ? imagesPdfBytes : new byte[0];
                }

                org.apache.pdfbox.multipdf.PDFMergerUtility merger = new org.apache.pdfbox.multipdf.PDFMergerUtility();
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                merger.setDestinationStream(out);
                if (imagesPdfBytes != null && imagesPdfBytes.length > 0) {
                        merger.addSource(new java.io.ByteArrayInputStream(imagesPdfBytes));
                }
                for (MultipartFile pdf : pdfs) {
                        merger.addSource(pdf.getInputStream());
                }
                merger.mergeDocuments(org.apache.pdfbox.io.MemoryUsageSetting.setupMainMemoryOnly());
                return out.toByteArray();
        }
}
