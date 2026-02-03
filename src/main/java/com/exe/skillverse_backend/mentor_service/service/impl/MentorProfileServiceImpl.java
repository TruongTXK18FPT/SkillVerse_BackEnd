package com.exe.skillverse_backend.mentor_service.service.impl;

import com.exe.skillverse_backend.mentor_service.dto.request.MentorProfileUpdateRequest;
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
import com.exe.skillverse_backend.course_service.repository.CoursePurchaseRepository;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import com.exe.skillverse_backend.shared.dto.MediaDTO;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.service.MediaService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MentorProfileServiceImpl implements MentorProfileService {

    private static final String MENTOR_PROFILE_NOT_FOUND = "MENTOR_PROFILE_NOT_FOUND";

    private final MentorProfileRepository mentorProfileRepository;
    private final PortfolioExtendedProfileRepository portfolioExtendedProfileRepository;
    private final BookingRepository bookingRepository;
    private final BookingReviewRepository bookingReviewRepository;
    private final CoursePurchaseRepository coursePurchaseRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final UserRepository userRepository;
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
        var badgesEarned = new java.util.HashSet<String>();
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
        java.math.BigDecimal revenue = coursePurchaseRepository.sumCapturedByMentor(mentorId)
                .orElse(java.math.BigDecimal.ZERO);

        java.util.List<BadgeInfo> catalog = java.util.List.of(
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
                            return java.util.Arrays.stream(objectMapper.readValue(profile.getSkills(), String[].class));
                        } else if (profile.getMainExpertiseAreas() != null) {
                            return java.util.Arrays.stream(profile.getMainExpertiseAreas().split(","));
                        }
                        return java.util.stream.Stream.empty();
                    } catch (JsonProcessingException e) {
                        log.error("Error parsing skills for user {}", profile.getUserId(), e);
                        return java.util.stream.Stream.empty();
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
