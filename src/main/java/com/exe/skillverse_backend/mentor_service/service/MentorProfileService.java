package com.exe.skillverse_backend.mentor_service.service;

import com.exe.skillverse_backend.mentor_service.dto.request.MentorProfileUpdateRequest;
import com.exe.skillverse_backend.mentor_service.dto.request.MentorSignatureDrawRequest;
import com.exe.skillverse_backend.mentor_service.dto.response.MentorProfileResponse;
import com.exe.skillverse_backend.mentor_service.dto.response.SkillTabResponse;
import java.util.List;

public interface MentorProfileService {

    /**
     * Get all approved mentors
     */
    List<MentorProfileResponse> getAllMentors();

    /**
     * Get mentor profile by user ID
     */
    MentorProfileResponse getMentorProfile(Long userId);

    /**
     * Update mentor profile
     */
    MentorProfileResponse updateMentorProfile(Long userId, MentorProfileUpdateRequest request);

    /**
     * Upload mentor avatar
     */
    String uploadMentorAvatar(Long userId, byte[] fileData, String fileName, String contentType);

    /**
     * Upload mentor signature image
     */
    String uploadMentorSignature(Long userId, byte[] fileData, String fileName, String contentType);

    /**
     * Generate mentor signature image from system drawing strokes only.
     */
    String createMentorSignatureFromDrawing(Long userId, MentorSignatureDrawRequest request);

    /**
     * Remove mentor signature image and fallback to platform verification.
     */
    void removeMentorSignature(Long userId);

    void setPreChatEnabled(Long userId, boolean enabled);

    /**
     * Get leaderboard of mentors ordered by level and points
     */
    List<MentorProfileResponse> getLeaderboard(int size);

    /**
     * Get all unique skills from all mentors
     */
    List<String> getAllSkills();

    /**
     * Get skill tab information for a mentor
     */
    SkillTabResponse getSkillTab(Long mentorId);

    /**
     * Get total students count across all mentor's courses
     */
    long getTotalStudentsCount(Long mentorId);

    /**
     * Find mentors by verified skill name (APPROVED status only)
     */
    List<MentorProfileResponse> findMentorsByVerifiedSkill(String skillName);

    /**
     * Get verified skills for a specific mentor
     */
    List<String> getVerifiedSkillsByMentorId(Long mentorId);

    /**
     * Check if mentor has a specific verified skill
     */
    boolean hasVerifiedSkill(Long mentorId, String skillName);
}
