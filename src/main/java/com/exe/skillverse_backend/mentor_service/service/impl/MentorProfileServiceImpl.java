package com.exe.skillverse_backend.mentor_service.service.impl;

import com.exe.skillverse_backend.mentor_service.dto.request.MentorProfileUpdateRequest;
import com.exe.skillverse_backend.mentor_service.dto.request.MentorSignatureDrawRequest;
import com.exe.skillverse_backend.mentor_service.dto.response.MentorProfileResponse;
import com.exe.skillverse_backend.mentor_service.dto.response.SkillTabResponse;
import com.exe.skillverse_backend.mentor_service.dto.response.BadgeInfo;
import com.exe.skillverse_backend.mentor_service.entity.ApplicationStatus;
import com.exe.skillverse_backend.mentor_service.entity.MentorProfile;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.mentor_service.service.MentorProfileService;
import com.exe.skillverse_backend.portfolio_service.entity.PortfolioExtendedProfile;
import com.exe.skillverse_backend.portfolio_service.repository.PortfolioExtendedProfileRepository;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingRepository;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingReviewRepository;
import com.exe.skillverse_backend.course_service.repository.CertificateRepository;
import com.exe.skillverse_backend.course_service.repository.CoursePurchaseRepository;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import com.exe.skillverse_backend.shared.repository.MediaRepository;
import com.exe.skillverse_backend.shared.dto.MediaDTO;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.service.MediaService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

@Slf4j
@Service
@RequiredArgsConstructor
public class MentorProfileServiceImpl implements MentorProfileService {

    private static final String MENTOR_PROFILE_NOT_FOUND = "MENTOR_PROFILE_NOT_FOUND";
    private static final int MAX_SIGNATURE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final int MIN_SIGNATURE_WIDTH = 240;
    private static final int MAX_SIGNATURE_WIDTH = 2400;
    private static final int MIN_SIGNATURE_HEIGHT = 60;
    private static final int MAX_SIGNATURE_HEIGHT = 600;
    private static final double MIN_SIGNATURE_ASPECT_RATIO = 2.0d;
    private static final double MAX_SIGNATURE_ASPECT_RATIO = 12.0d;
    private static final double MIN_SIGNATURE_FOREGROUND_RATIO = 0.001d;
    private static final double MAX_SIGNATURE_FOREGROUND_RATIO = 0.30d;
    private static final int MAX_SIGNATURE_STROKES = 120;
    private static final int MAX_POINTS_PER_STROKE = 2000;
    private static final int MAX_TOTAL_SIGNATURE_POINTS = 20000;
    private static final int MIN_TOTAL_SIGNATURE_POINTS = 10;
    private static final double MIN_STROKE_WIDTH = 1.0d;
    private static final double MAX_STROKE_WIDTH = 8.0d;

    private final MentorProfileRepository mentorProfileRepository;
    private final PortfolioExtendedProfileRepository portfolioExtendedProfileRepository;
    private final BookingRepository bookingRepository;
    private final BookingReviewRepository bookingReviewRepository;
    private final CertificateRepository certificateRepository;
    private final CoursePurchaseRepository coursePurchaseRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;
    private final MediaService mediaService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<MentorProfileResponse> getAllMentors() {
        return mentorProfileRepository.findByApplicationStatus(ApplicationStatus.APPROVED)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MentorProfileResponse> getLeaderboard(int size) {
        List<MentorProfile> approved = mentorProfileRepository.findByApplicationStatus(ApplicationStatus.APPROVED);
        return approved.stream()
                .sorted((a, b) -> {
                    int cmpLevel = Integer.compare(b.getCurrentLevel() != null ? b.getCurrentLevel() : 0,
                            a.getCurrentLevel() != null ? a.getCurrentLevel() : 0);
                    if (cmpLevel != 0)
                        return cmpLevel;
                    int cmpPoints = Integer.compare(b.getSkillPoints() != null ? b.getSkillPoints() : 0,
                            a.getSkillPoints() != null ? a.getSkillPoints() : 0);
                    if (cmpPoints != 0)
                        return cmpPoints;
                    return Double.compare(b.getRatingAverage() != null ? b.getRatingAverage() : 0.0,
                            a.getRatingAverage() != null ? a.getRatingAverage() : 0.0);
                })
                .limit(size)
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public MentorProfileResponse getMentorProfile(Long userId) {
        log.info("Getting mentor profile for user ID: {}", userId);

        MentorProfile profile = mentorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(MENTOR_PROFILE_NOT_FOUND));

        return mapToResponse(profile);
    }

    @Override
    @Transactional
    public MentorProfileResponse updateMentorProfile(Long userId, MentorProfileUpdateRequest request) {
        log.info("Updating mentor profile for user ID: {}", userId);

        MentorProfile profile = mentorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(MENTOR_PROFILE_NOT_FOUND));

        // Update fields if provided
        if (request.getFirstName() != null) {
            // Extract first name from full name or use provided first name
            String fullName = profile.getFullName();
            if (fullName != null && fullName.contains(" ")) {
                String[] nameParts = fullName.split(" ", 2);
                nameParts[0] = request.getFirstName();
                profile.setFullName(String.join(" ", nameParts));
            } else {
                profile.setFullName(
                        request.getFirstName() + (request.getLastName() != null ? " " + request.getLastName() : ""));
            }
        }

        if (request.getLastName() != null) {
            String fullName = profile.getFullName();
            if (fullName != null && fullName.contains(" ")) {
                String[] nameParts = fullName.split(" ", 2);
                if (nameParts.length > 1) {
                    nameParts[1] = request.getLastName();
                } else {
                    nameParts = new String[] { nameParts[0], request.getLastName() };
                }
                profile.setFullName(String.join(" ", nameParts));
            } else {
                profile.setFullName(
                        (profile.getFullName() != null ? profile.getFullName() : "") + " " + request.getLastName());
            }
        }

        if (request.getEmail() != null) {
            profile.setEmail(request.getEmail());
        }

        if (request.getBio() != null) {
            profile.setPersonalProfile(request.getBio());
        }

        if (request.getSpecialization() != null) {
            profile.setMainExpertiseAreas(request.getSpecialization());
        }

        if (request.getExperience() != null) {
            profile.setYearsOfExperience(request.getExperience());
        }

        if (request.getAvatar() != null) {
            profile.setAvatarUrl(request.getAvatar());
        }

        if (request.getSocialLinks() != null) {
            if (request.getSocialLinks().getLinkedin() != null) {
                profile.setLinkedinProfile(request.getSocialLinks().getLinkedin());
            }
            if (request.getSocialLinks().getGithub() != null) {
                profile.setGithubProfile(request.getSocialLinks().getGithub());
            }
            if (request.getSocialLinks().getWebsite() != null) {
                profile.setWebsiteUrl(request.getSocialLinks().getWebsite());
            }
        }
        if (request.getHourlyRate() != null) {
            profile.setHourlyRate(request.getHourlyRate());
        }

        if (request.getSkills() != null) {
            try {
                profile.setSkills(objectMapper.writeValueAsString(request.getSkills()));
            } catch (JsonProcessingException e) {
                log.error("Error serializing skills for user {}", userId, e);
            }
        }

        if (request.getAchievements() != null) {
            try {
                profile.setAchievements(objectMapper.writeValueAsString(request.getAchievements()));
            } catch (JsonProcessingException e) {
                log.error("Error serializing achievements for user {}", userId, e);
            }
        }

        profile.setUpdatedAt(LocalDateTime.now());

        MentorProfile savedProfile = mentorProfileRepository.save(profile);
        log.info("Mentor profile updated successfully for user ID: {}", userId);

        return mapToResponse(savedProfile);
    }

    @Override
    @Transactional
    public String uploadMentorAvatar(Long userId, byte[] fileData, String fileName, String contentType) {
        log.info("Uploading avatar for mentor user ID: {}", userId);

        MentorProfile profile = mentorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(MENTOR_PROFILE_NOT_FOUND));

        // Upload using MediaService
        MediaDTO mediaDto = mediaService.upload(
                userId,
                fileName,
                contentType,
                fileData.length,
                new ByteArrayInputStream(fileData));

        String avatarUrl = mediaDto.getUrl();

        // Update profile with avatar URL
        profile.setAvatarUrl(avatarUrl);
        profile.setUpdatedAt(LocalDateTime.now());
        mentorProfileRepository.save(profile);

        log.info("Avatar uploaded successfully for mentor user ID: {}", userId);
        return avatarUrl;
    }

    @Override
    @Transactional
    public String uploadMentorSignature(Long userId, byte[] fileData, String fileName, String contentType) {
        throw new BadRequestException("SIGNATURE_FILE_UPLOAD_DISABLED_USE_SYSTEM_SIGNING");
    }

    @Override
    @Transactional
    public String createMentorSignatureFromDrawing(Long userId, MentorSignatureDrawRequest request) {
        if (request == null) {
            throw new BadRequestException("SIGNATURE_DRAW_REQUEST_REQUIRED");
        }

        int canvasWidth = requireInRange(
                request.getCanvasWidth(),
                MIN_SIGNATURE_WIDTH,
                MAX_SIGNATURE_WIDTH,
                "SIGNATURE_DRAW_WIDTH_INVALID"
        );
        int canvasHeight = requireInRange(
                request.getCanvasHeight(),
                MIN_SIGNATURE_HEIGHT,
                MAX_SIGNATURE_HEIGHT,
                "SIGNATURE_DRAW_HEIGHT_INVALID"
        );

        List<MentorSignatureDrawRequest.Stroke> strokes = request.getStrokes();
        if (strokes == null || strokes.isEmpty()) {
            throw new BadRequestException("SIGNATURE_DRAW_STROKES_REQUIRED");
        }
        if (strokes.size() > MAX_SIGNATURE_STROKES) {
            throw new BadRequestException("SIGNATURE_DRAW_TOO_MANY_STROKES");
        }

        BufferedImage signatureImage = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = signatureImage.createGraphics();
        int totalPoints = 0;

        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, canvasWidth, canvasHeight);
            graphics.setColor(Color.BLACK);

            for (MentorSignatureDrawRequest.Stroke stroke : strokes) {
                if (stroke == null || stroke.getPoints() == null || stroke.getPoints().size() < 2) {
                    continue;
                }
                if (stroke.getPoints().size() > MAX_POINTS_PER_STROKE) {
                    throw new BadRequestException("SIGNATURE_DRAW_STROKE_TOO_LARGE");
                }

                double lineWidth = stroke.getLineWidth() == null ? 3.0d : stroke.getLineWidth();
                if (lineWidth < MIN_STROKE_WIDTH || lineWidth > MAX_STROKE_WIDTH) {
                    throw new BadRequestException("SIGNATURE_DRAW_STROKE_WIDTH_INVALID");
                }

                graphics.setStroke(new BasicStroke(
                        (float) lineWidth,
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND
                ));

                MentorSignatureDrawRequest.Point previous = null;
                for (MentorSignatureDrawRequest.Point point : stroke.getPoints()) {
                    if (point == null || point.getX() == null || point.getY() == null) {
                        throw new BadRequestException("SIGNATURE_DRAW_POINT_INVALID");
                    }
                    double x = point.getX();
                    double y = point.getY();
                    if (x < 0 || x > canvasWidth || y < 0 || y > canvasHeight) {
                        throw new BadRequestException("SIGNATURE_DRAW_POINT_OUT_OF_BOUNDS");
                    }
                    if (previous != null) {
                        graphics.drawLine(
                                (int) Math.round(previous.getX()),
                                (int) Math.round(previous.getY()),
                                (int) Math.round(x),
                                (int) Math.round(y)
                        );
                    }
                    previous = point;
                    totalPoints++;
                    if (totalPoints > MAX_TOTAL_SIGNATURE_POINTS) {
                        throw new BadRequestException("SIGNATURE_DRAW_TOO_MANY_POINTS");
                    }
                }
            }
        } finally {
            graphics.dispose();
        }

        if (totalPoints < MIN_TOTAL_SIGNATURE_POINTS) {
            throw new BadRequestException("SIGNATURE_DRAW_INSUFFICIENT_POINTS");
        }

        validateSignatureImage(signatureImage);
        byte[] normalizedSignature = encodeSignatureAsPng(signatureImage);
        if (normalizedSignature.length > MAX_SIGNATURE_SIZE_BYTES) {
            throw new BadRequestException("SIGNATURE_FILE_TOO_LARGE");
        }

        MentorProfile profile = mentorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(MENTOR_PROFILE_NOT_FOUND));

        MediaDTO mediaDto = mediaService.upload(
                userId,
                resolveSignatureFileName("mentor-signature-system"),
                "image/png",
                normalizedSignature.length,
                new ByteArrayInputStream(normalizedSignature));

        String signatureUrl = mediaDto.getUrl();

        profile.setSignatureUrl(signatureUrl);
        profile.setUpdatedAt(LocalDateTime.now());
        mentorProfileRepository.save(profile);

        log.info("System-drawn signature saved successfully for mentor user ID: {}", userId);
        return signatureUrl;
    }

    private int requireInRange(Integer value, int min, int max, String errorCode) {
        if (value == null || value < min || value > max) {
            throw new BadRequestException(errorCode);
        }
        return value;
    }

    private void validateSignatureImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        if (width < MIN_SIGNATURE_WIDTH || width > MAX_SIGNATURE_WIDTH
                || height < MIN_SIGNATURE_HEIGHT || height > MAX_SIGNATURE_HEIGHT) {
            throw new BadRequestException("SIGNATURE_IMAGE_DIMENSIONS_INVALID");
        }

        double aspectRatio = (double) width / (double) height;
        if (aspectRatio < MIN_SIGNATURE_ASPECT_RATIO || aspectRatio > MAX_SIGNATURE_ASPECT_RATIO) {
            throw new BadRequestException("SIGNATURE_IMAGE_ASPECT_RATIO_INVALID");
        }

        long foregroundPixels = 0L;
        long totalPixels = (long) width * height;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;

                boolean transparent = alpha < 20;
                boolean nearWhite = red > 245 && green > 245 && blue > 245;
                if (!transparent && !nearWhite) {
                    foregroundPixels++;
                }
            }
        }

        double foregroundRatio = (double) foregroundPixels / (double) totalPixels;
        if (foregroundRatio < MIN_SIGNATURE_FOREGROUND_RATIO || foregroundRatio > MAX_SIGNATURE_FOREGROUND_RATIO) {
            throw new BadRequestException("SIGNATURE_IMAGE_CONTENT_INVALID");
        }
    }

    private byte[] encodeSignatureAsPng(BufferedImage image) {
        BufferedImage normalized = new BufferedImage(
                image.getWidth(),
                image.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = normalized.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            boolean writeSuccess = ImageIO.write(normalized, "png", outputStream);
            if (!writeSuccess) {
                throw new BadRequestException("SIGNATURE_FILE_IMAGE_ENCODE_FAILED");
            }
            return outputStream.toByteArray();
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException("SIGNATURE_FILE_IMAGE_ENCODE_FAILED");
        }
    }

    private String resolveSignatureFileName(String originalFileName) {
        String suffix = ".png";
        if (originalFileName != null && !originalFileName.isBlank()) {
            String trimmed = originalFileName.trim();
            int extensionIndex = trimmed.lastIndexOf('.');
            String baseName = extensionIndex > 0 ? trimmed.substring(0, extensionIndex) : trimmed;
            String sanitizedBase = baseName.replaceAll("[^a-zA-Z0-9_-]", "-");
            if (!sanitizedBase.isBlank()) {
                return sanitizedBase + "-" + UUID.randomUUID() + suffix;
            }
        }
        return "mentor-signature-" + UUID.randomUUID() + suffix;
    }

    private String sanitizeSignatureUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }

        String normalized = rawUrl.trim()
                .replace("://res cloudinary com/", "://res.cloudinary.com/")
                .replace("://res%20cloudinary%20com/", "://res.cloudinary.com/");

        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return null;
            }
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return null;
            }
            return normalized;
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    @Override
    @Transactional
    public void removeMentorSignature(Long userId) {
        log.info("Removing signature for mentor user ID: {}", userId);

        MentorProfile profile = mentorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(MENTOR_PROFILE_NOT_FOUND));

        String signatureUrl = profile.getSignatureUrl();
        if (signatureUrl != null && !signatureUrl.isBlank()) {
            String rawTrimmed = signatureUrl.trim();
            String normalizedSignatureUrl = sanitizeSignatureUrl(signatureUrl);
            String lookupUrl = normalizedSignatureUrl != null ? normalizedSignatureUrl : rawTrimmed;
            long certificateRefCount = certificateRepository.countByInstructorSignatureUrlSnapshot(lookupUrl);
            if (!rawTrimmed.equals(lookupUrl)) {
                certificateRefCount += certificateRepository.countByInstructorSignatureUrlSnapshot(rawTrimmed);
            }
            if (certificateRefCount == 0) {
                mediaRepository.findFirstByUrl(lookupUrl)
                        .or(() -> {
                            if (!rawTrimmed.equals(lookupUrl)) {
                                return mediaRepository.findFirstByUrl(rawTrimmed);
                            }
                        return Optional.empty();
                        })
                        .ifPresent(media -> {
                            try {
                                mediaService.delete(media.getId(), userId);
                                log.info("Deleted signature media {} for mentor user ID: {}", media.getId(), userId);
                            } catch (Exception ex) {
                                log.warn(
                                        "Failed to delete signature media {} for mentor user ID {}: {}",
                                        media.getId(),
                                        userId,
                                        ex.getMessage()
                                );
                            }
                        });
            } else {
                log.info(
                        "Keeping signature file for mentor user ID {} because {} certificates reference it.",
                        userId,
                        certificateRefCount
                );
            }
        }

        profile.setSignatureUrl(null);
        profile.setUpdatedAt(LocalDateTime.now());
        mentorProfileRepository.save(profile);
    }

    @Override
    @Transactional
    public void setPreChatEnabled(Long userId, boolean enabled) {
        MentorProfile profile = mentorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(MENTOR_PROFILE_NOT_FOUND));
        profile.setPreChatEnabled(enabled);
        profile.setUpdatedAt(LocalDateTime.now());
        mentorProfileRepository.save(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public SkillTabResponse getSkillTab(Long mentorId) {
        MentorProfileResponse profile = getMentorProfile(mentorId);
        Set<String> badgesEarned = new HashSet<>();
        if (profile.getBadges() != null) {
            for (String b : profile.getBadges())
                if (b != null)
                    badgesEarned.add(b);
        }
        User mentorUser = userRepository.findById(mentorId)
                .orElseThrow(() -> new IllegalArgumentException("Mentor không tồn tại"));

        long sessionsCompleted = bookingRepository.countByMentorAndStatus(mentorUser,
                BookingStatus.COMPLETED);
        
        // Get all reviews for the mentor
        var allReviews = bookingReviewRepository.findByMentorIdOrderByCreatedAtDesc(mentorId);
        long totalReviews = allReviews.size();
        long fiveStar = allReviews.stream()
                .filter(r -> r.getRating() != null && r.getRating() == 5)
                .count();
        
        long sales = coursePurchaseRepository.countSuccessfulPurchasesByMentorId(mentorId);
        BigDecimal revenue = coursePurchaseRepository.sumCapturedByMentor(mentorId)
                .orElse(BigDecimal.ZERO);

        List<BadgeInfo> catalog = List.of(
                new BadgeInfo("FIRST_SESSION", "Buổi đầu tiên", "Hoàn thành buổi mentoring đầu tiên",
                        (int) sessionsCompleted,
                        1, badgesEarned.contains("FIRST_SESSION")),
                new BadgeInfo("TEN_SESSIONS", "10 buổi mentoring", "Hoàn thành 10 buổi mentoring",
                        (int) sessionsCompleted,
                        10, badgesEarned.contains("TEN_SESSIONS")),
                new BadgeInfo("HUNDRED_SESSIONS", "100 buổi mentoring", "Hoàn thành 100 buổi mentoring",
                        (int) sessionsCompleted, 100, badgesEarned.contains("HUNDRED_SESSIONS")),
                new BadgeInfo("FIRST_FIVE_STAR", "Đánh giá 5⭐ đầu tiên", "Nhận đánh giá 5 sao đầu tiên", (int) fiveStar,
                        1,
                        badgesEarned.contains("FIRST_FIVE_STAR")),
                new BadgeInfo("TEN_FIVE_STAR", "10 đánh giá 5⭐", "Nhận 10 đánh giá 5 sao", (int) fiveStar, 10,
                        badgesEarned.contains("TEN_FIVE_STAR")),
                new BadgeInfo("HUNDRED_FIVE_STAR", "100 đánh giá 5⭐", "Nhận 100 đánh giá 5 sao", (int) fiveStar, 100,
                        badgesEarned.contains("HUNDRED_FIVE_STAR")),
                new BadgeInfo("FIRST_COURSE_SALE", "Bán khóa học đầu tiên", "Bán được khóa học đầu tiên", (int) sales,
                        1,
                        badgesEarned.contains("FIRST_COURSE_SALE")),
                new BadgeInfo("TEN_COURSE_SALES", "Bán 10 khóa học", "Bán được 10 khóa học", (int) sales, 10,
                        badgesEarned.contains("TEN_COURSE_SALES")),
                new BadgeInfo("HUNDRED_COURSE_SALES", "Bán 100 khóa học", "Bán được 100 khóa học", (int) sales, 100,
                        badgesEarned.contains("HUNDRED_COURSE_SALES")));

        String levelTitle = getLevelTitle(profile.getCurrentLevel() != null ? profile.getCurrentLevel() : 0);
        int nextLevelPoints = computeNextLevelPoints(profile.getSkillPoints() != null ? profile.getSkillPoints() : 0);

        SkillTabResponse resp = new SkillTabResponse();
        resp.setSkillPoints(profile.getSkillPoints() != null ? profile.getSkillPoints() : 0);
        resp.setCurrentLevel(profile.getCurrentLevel() != null ? profile.getCurrentLevel() : 0);
        resp.setLevelTitle(levelTitle);
        resp.setSessionsCompleted((int) sessionsCompleted);
        resp.setFiveStarCount((int) fiveStar);
        resp.setTotalReviews((int) totalReviews);
        resp.setCourseSales((int) sales);
        resp.setRevenueVnd(revenue);
        resp.setNextLevelPoints(nextLevelPoints);
        resp.setBadges(catalog);
        return resp;
    }

    private String getLevelTitle(int level) {
        if (level == 1)
            return "Mentor mới nổi";
        if (level == 5)
            return "Mentor ngôi sao";
        if (level == 10)
            return "Mentor kỳ cựu";
        if (level == 15)
            return "Mentor cao thủ";
        if (level == 20)
            return "Mentor siêu cấp";
        return null;
    }

    private int computeNextLevelPoints(int points) {
        int remainder = points % 100;
        return 100 - remainder;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getAllSkills() {
        List<MentorProfile> profiles = mentorProfileRepository.findByApplicationStatus(ApplicationStatus.APPROVED);
        return profiles.stream()
                .flatMap(profile -> {
                    try {
                        if (profile.getSkills() != null) {
                            return Arrays.stream(objectMapper.readValue(profile.getSkills(), String[].class));
                        } else if (profile.getMainExpertiseAreas() != null) {
                            return Arrays.stream(profile.getMainExpertiseAreas().split(","));
                        }
                        return Stream.empty();
                    } catch (JsonProcessingException e) {
                        log.error("Error parsing skills for user {}", profile.getUserId(), e);
                        return Stream.empty();
                    }
                })
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private MentorProfileResponse mapToResponse(MentorProfile profile) {
        String fullName = profile.getFullName();
        String firstName = "";
        String lastName = "";

        if (fullName != null && fullName.contains(" ")) {
            String[] nameParts = fullName.split(" ", 2);
            firstName = nameParts[0];
            lastName = nameParts.length > 1 ? nameParts[1] : "";
        } else if (fullName != null) {
            firstName = fullName;
        }

        MentorProfileResponse.SocialLinks socialLinks = MentorProfileResponse.SocialLinks.builder()
                .linkedin(profile.getLinkedinProfile())
                .github(profile.getGithubProfile())
                .website(profile.getWebsiteUrl())
                .build();

        // Parse skills and achievements from text fields (you might want to store these
        // as JSON or separate tables)
        String[] skills = {};
        String[] achievements = {};
        String[] badges = {};

        try {
            if (profile.getSkills() != null) {
                skills = objectMapper.readValue(profile.getSkills(), String[].class);
            } else if (profile.getMainExpertiseAreas() != null) {
                // Fallback to old behavior if new field is empty
                skills = profile.getMainExpertiseAreas().split(",");
            }

            if (profile.getAchievements() != null) {
                achievements = objectMapper.readValue(profile.getAchievements(), String[].class);
            }
            if (profile.getBadges() != null) {
                badges = objectMapper.readValue(profile.getBadges(), String[].class);
            }
        } catch (JsonProcessingException e) {
            log.error("Error deserializing profile data for user {}", profile.getUserId(), e);
            // Fallback for skills if JSON parsing fails
            if (profile.getMainExpertiseAreas() != null) {
                skills = profile.getMainExpertiseAreas().split(",");
            }
        }

        // Fetch Portfolio Extended Profile to get hourlyRate and slug
        var portfolioProfile = portfolioExtendedProfileRepository.findByUserId(profile.getUserId());

        String slug = portfolioProfile
                .map(PortfolioExtendedProfile::getCustomUrlSlug)
                .orElse(null);

        Double hourlyRate = profile.getHourlyRate() != null
                ? profile.getHourlyRate()
                : portfolioProfile.map(PortfolioExtendedProfile::getHourlyRate).orElse(null);

        return MentorProfileResponse.builder()
                .id(profile.getUserId())
                .firstName(firstName)
                .lastName(lastName)
                .email(profile.getEmail())
                .bio(profile.getPersonalProfile())
                .specialization(profile.getMainExpertiseAreas())
                .experience(profile.getYearsOfExperience())
                .avatar(profile.getAvatarUrl())
                .signatureUrl(profile.getSignatureUrl())
                .socialLinks(socialLinks)
                .skills(skills)
                .achievements(achievements)
                .ratingAverage(profile.getRatingAverage())
                .ratingCount(profile.getRatingCount())
                .hourlyRate(hourlyRate)
                .preChatEnabled(profile.getPreChatEnabled())
                .slug(slug)
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .skillPoints(profile.getSkillPoints())
                .currentLevel(profile.getCurrentLevel())
                .badges(badges)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public long getTotalStudentsCount(Long mentorId) {
        log.info("Getting total students count for mentor ID: {}", mentorId);
        return courseEnrollmentRepository.countTotalStudentsByMentorId(mentorId);
    }
}
