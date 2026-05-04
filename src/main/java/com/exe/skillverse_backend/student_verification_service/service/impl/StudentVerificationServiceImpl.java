package com.exe.skillverse_backend.student_verification_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.service.CloudinaryService;
import com.exe.skillverse_backend.shared.service.EmailService;
import com.exe.skillverse_backend.student_verification_service.config.StudentVerificationProperties;
import com.exe.skillverse_backend.student_verification_service.dto.response.StudentVerificationDetailResponse;
import com.exe.skillverse_backend.student_verification_service.dto.response.StudentVerificationEligibilityResponse;
import com.exe.skillverse_backend.student_verification_service.dto.response.StudentVerificationListItemResponse;
import com.exe.skillverse_backend.student_verification_service.dto.response.StudentVerificationStartResponse;
import com.exe.skillverse_backend.student_verification_service.entity.StudentVerificationRequest;
import com.exe.skillverse_backend.student_verification_service.enums.StudentVerificationStatus;
import com.exe.skillverse_backend.student_verification_service.enums.StudentVerificationStorageProvider;
import com.exe.skillverse_backend.student_verification_service.repository.StudentVerificationRequestRepository;
import com.exe.skillverse_backend.student_verification_service.service.StudentVerificationService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentVerificationServiceImpl implements StudentVerificationService {

    private static final String LOCAL_TEMP_DIR = "temp";
    private static final String LOCAL_FINAL_DIR = "final";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StudentVerificationRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final CloudinaryService cloudinaryService;
    private final StudentVerificationProperties properties;

    // [Nghiep vu] Bat dau yeu cau xac thuc sinh vien voi OTP email truong va luu tam anh the de doi xac minh OTP.
    @Override
    @Transactional
    public StudentVerificationStartResponse startVerification(Long userId, String schoolEmail, MultipartFile studentCardImage) {
        ensureFeatureEnabled();
        User user = getUserOrThrow(userId);

        String normalizedSchoolEmail = normalizeSchoolEmail(schoolEmail);
        String schoolDomain = extractDomain(normalizedSchoolEmail);
        validateSchoolDomain(schoolDomain);
        ensureSchoolEmailNotBoundToOtherUser(normalizedSchoolEmail, userId);
        validateImageFile(studentCardImage);

        Optional<StudentVerificationRequest> latestOptional =
                requestRepository.findTopByUser_IdOrderByCreatedAtDesc(userId);

        StudentVerificationRequest request;
        if (latestOptional.isPresent()) {
            StudentVerificationRequest latest = latestOptional.get();
            if (latest.getStatus() == StudentVerificationStatus.PENDING_REVIEW) {
                throw new BadRequestException("You already have a verification request under review");
            }
            if (latest.getStatus() == StudentVerificationStatus.APPROVED) {
                throw new BadRequestException("Your student verification is already approved");
            }

            if (latest.getStatus() == StudentVerificationStatus.EMAIL_OTP_PENDING) {
                request = latest;
                cleanupStoredPathQuietly(request.getTempImagePath());
            } else {
                request = StudentVerificationRequest.builder()
                        .user(user)
                        .build();
            }
        } else {
            request = StudentVerificationRequest.builder()
                    .user(user)
                    .build();
        }

        String otp = applyOtp(request);
        request.setSchoolEmail(normalizedSchoolEmail);
        request.setSchoolDomain(schoolDomain);
        request.setEmailDomainValid(Boolean.TRUE);
        request.setStatus(StudentVerificationStatus.EMAIL_OTP_PENDING);
        request.setOtpVerifiedAt(null);
        request.setReviewNote(null);
        request.setRejectionReason(null);
        request.setReviewedAt(null);
        request.setReviewedBy(null);
        request.setImageProvider(null);
        request.setImagePublicId(null);
        request.setImageStoragePath(null);
        request.setImageUrl(null);
        request.setUploadedFileName(safeTrim(studentCardImage.getOriginalFilename()));
        request.setUploadedContentType(safeTrim(studentCardImage.getContentType()));
        request.setUploadedFileSize(studentCardImage.getSize());

        request = requestRepository.save(request);

        String tempImagePath = storeTempImage(request.getId(), studentCardImage);
        request.setTempImagePath(tempImagePath);
        request = requestRepository.save(request);

        try {
            sendOtpEmail(request.getSchoolEmail(), otp);
        } catch (Exception e) {
            cleanupStoredPathQuietly(request.getTempImagePath());
            throw e;
        }

        log.info("Started student verification request {} for user {}", request.getId(), userId);
        return StudentVerificationStartResponse.builder()
                .requestId(request.getId())
                .status(request.getStatus())
                .otpExpiresAt(request.getOtpExpiresAt())
                .message("OTP has been sent to your school email")
                .build();
    }

    // [Nghiep vu] Cho phep gui lai OTP neu nguoi hoc chua xac minh email truong va da qua cooldown.
    @Override
    @Transactional
    public StudentVerificationStartResponse resendOtp(Long userId, Long requestId) {
        ensureFeatureEnabled();

        StudentVerificationRequest request = getRequestForUser(userId, requestId);
        if (request.getStatus() != StudentVerificationStatus.EMAIL_OTP_PENDING) {
            throw new BadRequestException("OTP can only be resent for pending email verification requests");
        }

        if (request.getLastOtpSentAt() != null) {
            long elapsedSeconds = Duration.between(request.getLastOtpSentAt(), LocalDateTime.now()).toSeconds();
            if (elapsedSeconds < properties.getOtp().getResendCooldownSeconds()) {
                long remaining = properties.getOtp().getResendCooldownSeconds() - elapsedSeconds;
                throw new BadRequestException("Please wait " + remaining + " seconds before requesting a new OTP");
            }
        }

        String otp = applyOtp(request);
        request = requestRepository.save(request);

        sendOtpEmail(request.getSchoolEmail(), otp);

        return StudentVerificationStartResponse.builder()
                .requestId(request.getId())
                .status(request.getStatus())
                .otpExpiresAt(request.getOtpExpiresAt())
                .message("A new OTP has been sent to your school email")
                .build();
    }

    // [Nghiep vu] Sau khi OTP hop le, ho so duoc dua vao hang doi de admin review thu cong tren anh the da upload.
    @Override
    @Transactional(noRollbackFor = BadRequestException.class)
    public StudentVerificationDetailResponse verifyOtpAndSubmit(Long userId, Long requestId, String otp) {
        ensureFeatureEnabled();

        StudentVerificationRequest request = getRequestForUser(userId, requestId);
        if (request.getStatus() != StudentVerificationStatus.EMAIL_OTP_PENDING) {
            throw new BadRequestException("This request is not waiting for OTP verification");
        }

        validateOtp(request, otp);

        request.setOtpVerifiedAt(LocalDateTime.now());
        request.setOtpHash(null);
        request.setOtpExpiresAt(null);
        request.setOtpAttempts(0);

        finalizeImageStorage(request);

        request.setStatus(StudentVerificationStatus.PENDING_REVIEW);
        request.setReviewNote(null);
        request.setRejectionReason(null);
        request.setReviewedAt(null);
        request.setReviewedBy(null);

        request = requestRepository.save(request);

        return toDetailResponse(request, false);
    }

    // [Nghiep vu] Tra ve yeu cau gan nhat de student xem tien do xac thuc.
    @Override
    @Transactional(readOnly = true)
    public StudentVerificationDetailResponse getLatestMyRequest(Long userId) {
        ensureFeatureEnabled();

        StudentVerificationRequest request = requestRepository.findTopByUser_IdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new NotFoundException("No student verification request found"));

        return toDetailResponse(request, false);
    }

    // [Nghiep vu] Student xem chi tiet mot request cua chinh minh.
    @Override
    @Transactional(readOnly = true)
    public StudentVerificationDetailResponse getMyRequestDetail(Long userId, Long requestId) {
        ensureFeatureEnabled();
        StudentVerificationRequest request = getRequestForUser(userId, requestId);
        return toDetailResponse(request, false);
    }

    // [Nghiep vu] Premium student pack duoc mo khi ton tai request da duoc admin approve.
    @Override
    @Transactional(readOnly = true)
    public StudentVerificationEligibilityResponse getMyEligibility(Long userId) {
        ensureFeatureEnabled();

        Optional<StudentVerificationRequest> approvedRequest =
                requestRepository.findTopByUser_IdAndStatusOrderByReviewedAtDesc(userId, StudentVerificationStatus.APPROVED);

        boolean approved = approvedRequest.isPresent();
        return StudentVerificationEligibilityResponse.builder()
                .approved(approved)
                .canBuyStudentPremium(approved)
                .lastApprovedAt(approved ? approvedRequest.get().getReviewedAt() : null)
                .message(approved
                        ? "Student verification approved. You can buy the student premium plan"
                        : "Student verification is required before purchasing the student premium plan")
                .build();
    }

    // [Nghiep vu] Admin can danh sach request theo trang thai de review tap trung.
    @Override
    @Transactional(readOnly = true)
    public Page<StudentVerificationListItemResponse> getRequestsForAdmin(StudentVerificationStatus status, Pageable pageable) {
        ensureFeatureEnabled();

        Page<StudentVerificationRequest> page;
        if (status == null) {
            page = requestRepository.findAllByOrderByCreatedAtDesc(pageable);
        } else {
            page = requestRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        }

        return page.map(this::toListItemResponse);
    }

    // [Nghiep vu] Admin can xem chi tiet request + anh de phe duyet ho so.
    @Override
    @Transactional(readOnly = true)
    public StudentVerificationDetailResponse getRequestDetailForAdmin(Long requestId) {
        ensureFeatureEnabled();

        StudentVerificationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Student verification request not found"));

        return toDetailResponse(request, true);
    }

    // [Nghiep vu] Approve request se mo quyen mua student premium cho user.
    @Override
    @Transactional
    public StudentVerificationDetailResponse approveRequest(Long adminId, Long requestId, String reviewNote) {
        ensureFeatureEnabled();

        User admin = getUserOrThrow(adminId);
        StudentVerificationRequest request = getRequestForAdminReview(requestId);
        ensureSchoolEmailNotBoundToOtherUser(request.getSchoolEmail(), request.getUser().getId());

        request.setStatus(StudentVerificationStatus.APPROVED);
        request.setReviewedBy(admin);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewNote(safeTrim(reviewNote));
        request.setRejectionReason(null);

        request = requestRepository.save(request);
        return toDetailResponse(request, true);
    }

    // [Nghiep vu] Admin co the go rang buoc email truong da duoc duyet khi ho so het han de account khac duoc xac minh lai.
    @Override
    @Transactional
    public StudentVerificationDetailResponse expireApprovedEmail(Long adminId, Long requestId, String reason) {
        ensureFeatureEnabled();

        User admin = getUserOrThrow(adminId);
        StudentVerificationRequest request = getApprovedRequestForEmailRelease(requestId);

        String normalizedReason = safeTrim(reason);
        if (normalizedReason == null || normalizedReason.isBlank()) {
            throw new BadRequestException("Expiration reason is required");
        }

        request.setStatus(StudentVerificationStatus.EXPIRED);
        request.setReviewedBy(admin);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewNote(normalizedReason);
        request.setRejectionReason(normalizedReason);

        request = requestRepository.save(request);
        return toDetailResponse(request, true);
    }

    // [Nghiep vu] Reject request can ghi ro ly do de student cap nhat ho so va nop lai.
    @Override
    @Transactional
    public StudentVerificationDetailResponse rejectRequest(Long adminId, Long requestId, String reason) {
        ensureFeatureEnabled();

        User admin = getUserOrThrow(adminId);
        StudentVerificationRequest request = getRequestForAdminReview(requestId);

        String normalizedReason = safeTrim(reason);
        if (normalizedReason == null || normalizedReason.isBlank()) {
            throw new BadRequestException("Rejection reason is required");
        }

        request.setStatus(StudentVerificationStatus.REJECTED);
        request.setReviewedBy(admin);
        request.setReviewedAt(LocalDateTime.now());
        request.setRejectionReason(normalizedReason);
        request.setReviewNote(null);

        request = requestRepository.save(request);
        return toDetailResponse(request, true);
    }

    // [Nghiep vu] Student duoc xem anh the local da upload cua chinh request minh.
    @Override
    @Transactional(readOnly = true)
    public LocalImagePayload getLocalImageForUser(Long userId, Long requestId) {
        ensureFeatureEnabled();
        StudentVerificationRequest request = getRequestForUser(userId, requestId);
        return buildLocalImagePayload(request);
    }

    // [Nghiep vu] Admin xem anh local de review truc quan trong qua trinh duyet.
    @Override
    @Transactional(readOnly = true)
    public LocalImagePayload getLocalImageForAdmin(Long requestId) {
        ensureFeatureEnabled();

        StudentVerificationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Student verification request not found"));

        return buildLocalImagePayload(request);
    }

    // [Nghiep vu] Ham dung cho premium module check gate student pack.
    @Override
    @Transactional(readOnly = true)
    public boolean hasApprovedStudentVerification(Long userId) {
        ensureFeatureEnabled();
        return requestRepository.existsByUser_IdAndStatus(userId, StudentVerificationStatus.APPROVED);
    }

    private StudentVerificationRequest getRequestForUser(Long userId, Long requestId) {
        return requestRepository.findByIdAndUser_Id(requestId, userId)
                .orElseThrow(() -> new NotFoundException("Student verification request not found"));
    }

    private StudentVerificationRequest getRequestForAdminReview(Long requestId) {
        StudentVerificationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Student verification request not found"));

        if (request.getStatus() != StudentVerificationStatus.PENDING_REVIEW) {
            throw new BadRequestException("Only PENDING_REVIEW requests can be processed");
        }
        return request;
    }

    private StudentVerificationRequest getApprovedRequestForEmailRelease(Long requestId) {
        StudentVerificationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Student verification request not found"));

        if (request.getStatus() != StudentVerificationStatus.APPROVED) {
            throw new BadRequestException("Only APPROVED requests can be expired");
        }
        return request;
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private void ensureFeatureEnabled() {
        if (!properties.isEnabled()) {
            throw new BadRequestException("Student verification feature is disabled");
        }
    }

    private String normalizeSchoolEmail(String schoolEmail) {
        if (schoolEmail == null || schoolEmail.isBlank()) {
            throw new BadRequestException("School email is required");
        }

        String normalized = schoolEmail.trim().toLowerCase(Locale.ROOT);
        int atIndex = normalized.lastIndexOf('@');
        if (atIndex <= 0 || atIndex == normalized.length() - 1) {
            throw new BadRequestException("School email format is invalid");
        }

        return normalized;
    }

    private String extractDomain(String email) {
        int atIndex = email.lastIndexOf('@');
        return email.substring(atIndex + 1).toLowerCase(Locale.ROOT);
    }

    private void validateSchoolDomain(String domain) {
        Set<String> allowedDomainSet = properties.getAllowedSchoolDomains().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT).trim())
                .collect(java.util.stream.Collectors.toSet());

        boolean exactMatch = allowedDomainSet.contains(domain);

        boolean suffixMatch = properties.getAllowedDomainSuffixes().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT).trim())
                .anyMatch(domain::endsWith);

        if (!(exactMatch || suffixMatch)) {
            throw new BadRequestException("School email domain is not allowed");
        }
    }

    private void ensureSchoolEmailNotBoundToOtherUser(String schoolEmail, Long userId) {
        boolean inUseByAnotherUser = requestRepository.existsBySchoolEmailAndStatusAndUser_IdNot(
                schoolEmail,
                StudentVerificationStatus.APPROVED,
                userId
        );
        if (inUseByAnotherUser) {
            throw new BadRequestException("This school email is already verified by another account");
        }
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Student card image is required");
        }

        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new BadRequestException("Image size exceeds maximum allowed size");
        }

        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT).trim();

        boolean mimeAllowed = properties.getAllowedMimeTypes().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT).trim())
                .anyMatch(contentType::equals);

        if (!mimeAllowed) {
            throw new BadRequestException("Only JPG and PNG images are allowed");
        }
    }

    private String applyOtp(StudentVerificationRequest request) {
        String otp = generateOtp();
        request.setOtpHash(hashValue(otp));
        request.setOtpExpiresAt(LocalDateTime.now().plusMinutes(properties.getOtp().getExpiryMinutes()));
        request.setOtpAttempts(0);
        request.setLastOtpSentAt(LocalDateTime.now());
        return otp;
    }

    private String generateOtp() {
        int otpLength = Math.max(4, properties.getOtp().getLength());
        int min = (int) Math.pow(10, otpLength - 1);
        int bound = (int) Math.pow(10, otpLength) - min;
        return String.valueOf(min + SECURE_RANDOM.nextInt(bound));
    }

    private String hashValue(String plainValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(plainValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private void sendOtpEmail(String schoolEmail, String otp) {
        int expiryMinutes = Math.max(1, properties.getOtp().getExpiryMinutes());

                String subject = "[SkillVerse] Mã OTP xác thực sinh viên";
                String html = """
                                <!doctype html>
                                <html lang=\"vi\">
                                <head>
                                    <meta charset=\"UTF-8\" />
                                    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />
                                    <title>OTP xác thực sinh viên</title>
                                    <style>
                                        body { margin:0; padding:0; background:#f3f6fb; font-family:Arial, Helvetica, sans-serif; color:#132238; }
                                        .wrapper { width:100%%; background:#f3f6fb; }
                                        .container { width:640px; max-width:640px; border:1px solid #d9e4f1; border-radius:16px; overflow:hidden; background:#ffffff; }
                                        .header { padding:22px 18px; background:#061322; background-image:linear-gradient(120deg,#071321 0%%,#0a1f35 52%%,#0f3b63 100%%); border-bottom:1px solid #1c4d7a; text-align:center; }
                                        .logo { width:138px; max-width:138px; height:auto; display:block; margin:0 auto; }
                                        .badge { display:inline-block; margin-top:12px; padding:6px 12px; border-radius:999px; background:#0c2138; color:#6de9ff; border:1px solid #24c8f5; font-size:11px; font-weight:700; letter-spacing:0.4px; }
                                        .content { padding:24px; }
                                        h1 { margin:0 0 12px 0; font-size:24px; line-height:1.3; color:#10263f; }
                                        p { margin:0 0 10px 0; font-size:14px; line-height:1.7; color:#344a63; }
                                        .otp-wrap { margin:16px 0 14px; border:1px dashed #c8d9ec; border-radius:12px; background:#f8fbff; text-align:center; padding:16px 12px; }
                                        .otp-label { font-size:12px; color:#5d7692; letter-spacing:0.4px; margin-bottom:4px; }
                                        .otp-code { font-size:34px; letter-spacing:6px; line-height:1.2; font-weight:700; color:#0f75bc; }
                                        .note { margin-top:12px; padding:12px; border-radius:10px; background:#f9fcff; border:1px solid #dbe6f3; color:#506883; font-size:13px; }
                                        .footer { padding:14px 20px 20px; border-top:1px solid #e6eef8; text-align:center; font-size:12px; color:#6c8098; background:#fbfdff; }
                                    </style>
                                </head>
                                <body>
                                    <table role=\"presentation\" class=\"wrapper\" cellpadding=\"0\" cellspacing=\"0\">
                                        <tr>
                                            <td align=\"center\" style=\"padding:24px 12px;\">
                                                <table role=\"presentation\" class=\"container\" cellpadding=\"0\" cellspacing=\"0\">
                                                    <tr>
                                                        <td class=\"header\">
                                                            <img class=\"logo\" src=\"cid:skillverse-logo\" alt=\"SkillVerse\" />
                                                            <div class=\"badge\">XÁC THỰC EMAIL TRƯỜNG</div>
                                                        </td>
                                                    </tr>
                                                    <tr>
                                                        <td class=\"content\">
                                                            <h1>Mã OTP xác thực sinh viên</h1>
                                                            <p>Kính gửi bạn,</p>
                                                            <p>Vui lòng sử dụng mã OTP bên dưới để hoàn tất bước xác thực email trường trong hệ thống SkillVerse.</p>
                                                            <div class=\"otp-wrap\">
                                                                <div class=\"otp-label\">MÃ OTP</div>
                                                                <div class=\"otp-code\">%s</div>
                                                            </div>
                                                            <div class=\"note\">
                                                                Mã có hiệu lực trong <strong>%d phút</strong>. Vui lòng không chia sẻ mã cho người khác để bảo mật tài khoản.
                                                            </div>
                                                            <p style=\"margin-top:12px;color:#6c8098;font-size:13px;\">Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email.</p>
                                                        </td>
                                                    </tr>
                                                    <tr>
                                                        <td class=\"footer\">© 2026 SkillVerse. Email được gửi tự động từ hệ thống xác thực sinh viên.</td>
                                                    </tr>
                                                </table>
                                            </td>
                                        </tr>
                                    </table>
                                </body>
                                </html>
                                """.formatted(otp, expiryMinutes);

        emailService.sendHtmlEmail(schoolEmail, subject, html);
    }

    private void validateOtp(StudentVerificationRequest request, String providedOtp) {
        String normalizedOtp = providedOtp == null ? "" : providedOtp.trim();
        if (!normalizedOtp.matches("^\\d+$")) {
            throw new BadRequestException("OTP format is invalid");
        }

        if (request.getOtpHash() == null || request.getOtpExpiresAt() == null) {
            throw new BadRequestException("No active OTP found. Please request a new OTP");
        }

        if (LocalDateTime.now().isAfter(request.getOtpExpiresAt())) {
            request.setOtpHash(null);
            request.setOtpExpiresAt(null);
            request.setOtpAttempts(0);
            requestRepository.save(request);
            throw new BadRequestException("OTP has expired. Please request a new OTP");
        }

        int maxAttempts = Math.max(1, properties.getOtp().getMaxAttempts());
        if (request.getOtpAttempts() >= maxAttempts) {
            throw new BadRequestException("Maximum OTP attempts exceeded. Please request a new OTP");
        }

        if (!hashValue(normalizedOtp).equals(request.getOtpHash())) {
            request.setOtpAttempts(request.getOtpAttempts() + 1);
            requestRepository.save(request);
            int remainingAttempts = Math.max(0, maxAttempts - request.getOtpAttempts());
            throw new BadRequestException("Invalid OTP. Remaining attempts: " + remainingAttempts);
        }
    }

    private void finalizeImageStorage(StudentVerificationRequest request) {
        Path tempImage;
        try {
            tempImage = resolveStoredPath(request.getTempImagePath());
        } catch (BadRequestException e) {
            throw new IllegalStateException("Invalid temporary image path for final storage", e);
        }

        if (!Files.exists(tempImage)) {
            throw new IllegalStateException("Uploaded student card image is missing");
        }

        try {
            StudentVerificationStorageProvider storageProvider = properties.getStorageProvider();
            if (storageProvider == StudentVerificationStorageProvider.CLOUDINARY) {
                byte[] imageBytes = Files.readAllBytes(tempImage);
                String fileName = resolveUploadFileName(request);
                String contentType = request.getUploadedContentType();

                MultipartFile uploadFile = new InMemoryMultipartFile(fileName, contentType, imageBytes);
                Map<String, Object> uploaded = cloudinaryService.uploadImage(uploadFile, properties.getCloudinaryFolder());

                request.setImageProvider(StudentVerificationStorageProvider.CLOUDINARY);
                request.setImagePublicId(asString(uploaded.get("public_id")));
                request.setImageStoragePath(request.getImagePublicId());
                request.setImageUrl(resolveCloudinaryUrl(uploaded));
            } else {
                Path finalPath = buildFinalLocalPath(request);
                Files.createDirectories(finalPath.getParent());
                Files.move(tempImage, finalPath, StandardCopyOption.REPLACE_EXISTING);

                request.setImageProvider(StudentVerificationStorageProvider.LOCAL);
                request.setImageStoragePath(toStorageRelativePath(finalPath));
                request.setImagePublicId(null);
                request.setImageUrl(null);
            }

            cleanupStoredPathQuietly(request.getTempImagePath());
            request.setTempImagePath(null);

        } catch (IOException e) {
            log.error("Failed to persist student card image for request {}", request.getId(), e);
            throw new RuntimeException("Failed to store student card image", e);
        }
    }

    private Path buildFinalLocalPath(StudentVerificationRequest request) {
        String extension = extensionFromFileName(resolveUploadFileName(request));
        String finalName = "request-" + request.getId() + "-" + UUID.randomUUID() + extension;
        return resolveBaseStoragePath()
                .resolve(LOCAL_FINAL_DIR)
                .resolve(finalName)
                .normalize()
                .toAbsolutePath();
    }

    private String storeTempImage(Long requestId, MultipartFile file) {
        try {
            Path basePath = resolveBaseStoragePath();
            Path tempDir = basePath.resolve(LOCAL_TEMP_DIR).toAbsolutePath().normalize();
            Files.createDirectories(tempDir);

            String extension = extensionFromFileName(file.getOriginalFilename());
            String tempFileName = "request-" + requestId + "-" + UUID.randomUUID() + extension;
            Path tempPath = tempDir.resolve(tempFileName).normalize().toAbsolutePath();

            if (!tempPath.startsWith(basePath)) {
                throw new BadRequestException("Invalid temp image storage path");
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, tempPath, StandardCopyOption.REPLACE_EXISTING);
            }

            return toStorageRelativePath(tempPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store temporary student card image", e);
        }
    }

    private LocalImagePayload buildLocalImagePayload(StudentVerificationRequest request) {
        if (request.getImageProvider() != StudentVerificationStorageProvider.LOCAL) {
            throw new BadRequestException("This request image is not stored locally");
        }

        Path imagePath = resolveStoredPath(request.getImageStoragePath());
        if (!Files.exists(imagePath)) {
            throw new NotFoundException("Student card image file not found");
        }

        try {
            Resource resource = new UrlResource(imagePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new NotFoundException("Student card image file is not readable");
            }

            String contentType = request.getUploadedContentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = Files.probeContentType(imagePath);
            }
            if (contentType == null || contentType.isBlank()) {
                contentType = "application/octet-stream";
            }

            String fileName = resolveUploadFileName(request);
            return new LocalImagePayload(resource, contentType, fileName);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load local image", e);
        }
    }

    private StudentVerificationDetailResponse toDetailResponse(StudentVerificationRequest request, boolean forAdmin) {
        String imageUrl = request.getImageUrl();
        if (request.getImageProvider() == StudentVerificationStorageProvider.LOCAL) {
            imageUrl = forAdmin
                    ? "/api/admin/student-verifications/requests/%d/image".formatted(request.getId())
                    : "/api/student-verifications/requests/%d/image".formatted(request.getId());
        }

        return StudentVerificationDetailResponse.builder()
                .id(request.getId())
                .userId(request.getUser().getId())
                .userEmail(request.getUser().getEmail())
                .userFullName(request.getUser().getFullName())
                .schoolEmail(request.getSchoolEmail())
                .schoolDomain(request.getSchoolDomain())
                .emailDomainValid(Boolean.TRUE.equals(request.getEmailDomainValid()))
                .status(request.getStatus())
                .otpExpiresAt(request.getOtpExpiresAt())
                .otpVerifiedAt(request.getOtpVerifiedAt())
                .imageUrl(imageUrl)
                .uploadedFileName(request.getUploadedFileName())
                .uploadedContentType(request.getUploadedContentType())
                .uploadedFileSize(request.getUploadedFileSize())
                .reviewNote(request.getReviewNote())
                .rejectionReason(request.getRejectionReason())
                .reviewedById(request.getReviewedBy() != null ? request.getReviewedBy().getId() : null)
                .reviewedAt(request.getReviewedAt())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }

    private StudentVerificationListItemResponse toListItemResponse(StudentVerificationRequest request) {
        return StudentVerificationListItemResponse.builder()
                .id(request.getId())
                .userId(request.getUser().getId())
                .userEmail(request.getUser().getEmail())
                .userFullName(request.getUser().getFullName())
                .schoolEmail(request.getSchoolEmail())
                .status(request.getStatus())
                .createdAt(request.getCreatedAt())
                .reviewedAt(request.getReviewedAt())
                .build();
    }

    private Path resolveBaseStoragePath() {
        Path path = Paths.get(properties.getLocalStoragePath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create local student verification storage directory", e);
        }
        return path;
    }

    private Path resolveStoredPath(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            throw new BadRequestException("Stored image path is empty");
        }

        Path base = resolveBaseStoragePath();
        Path candidate = Paths.get(storedPath);

        Path resolved = candidate.isAbsolute()
                ? candidate.normalize().toAbsolutePath()
                : base.resolve(candidate).normalize().toAbsolutePath();

        if (!resolved.startsWith(base)) {
            throw new BadRequestException("Invalid stored image path");
        }

        return resolved;
    }

    private String toStorageRelativePath(Path absolutePath) {
        Path relative = resolveBaseStoragePath().relativize(absolutePath.toAbsolutePath().normalize());
        return relative.toString().replace('\\', '/');
    }

    private void cleanupStoredPathQuietly(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return;
        }

        try {
            Path resolved = resolveStoredPath(storedPath);
            Files.deleteIfExists(resolved);
        } catch (Exception e) {
            log.debug("Failed to cleanup temporary path {}", storedPath, e);
        }
    }

    private String extensionFromFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return ".jpg";
        }

        String trimmed = fileName.trim();
        int lastDot = trimmed.lastIndexOf('.');
        if (lastDot <= -1 || lastDot == trimmed.length() - 1) {
            return ".jpg";
        }

        String ext = trimmed.substring(lastDot).toLowerCase(Locale.ROOT);
        if (ext.equals(".jpeg")) {
            return ".jpg";
        }
        if (ext.equals(".jpg") || ext.equals(".png")) {
            return ext;
        }

        return ".jpg";
    }

    private String resolveUploadFileName(StudentVerificationRequest request) {
        if (request.getUploadedFileName() != null && !request.getUploadedFileName().isBlank()) {
            return request.getUploadedFileName().trim();
        }
        String extension = extensionFromFileName(null);
        return "student-card-" + request.getId() + extension;
    }

    private String resolveCloudinaryUrl(Map<String, Object> uploaded) {
        String secureUrl = asString(uploaded.get("secure_url"));
        if (secureUrl != null && !secureUrl.isBlank()) {
            return secureUrl;
        }
        return asString(uploaded.get("url"));
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String safeTrim(String value) {
        return value == null ? null : value.trim();
    }

    private static class InMemoryMultipartFile implements MultipartFile {

        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        InMemoryMultipartFile(String originalFilename, String contentType, byte[] content) {
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content;
        }

        @Override
        public String getName() {
            return originalFilename;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            Files.copy(getInputStream(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
