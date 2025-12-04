package com.exe.skillverse_backend.mentor_service.service;

import com.exe.skillverse_backend.mentor_service.dto.request.MentorProfileUpdateRequest;
import com.exe.skillverse_backend.mentor_service.dto.response.MentorProfileResponse;

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

    void setPreChatEnabled(Long userId, boolean enabled);

    /**
     * Get leaderboard of mentors ordered by level and points
     */
    java.util.List<MentorProfileResponse> getLeaderboard(int size);

    /**
     * Get all unique skills from all mentors
     */
    List<String> getAllSkills();
}
