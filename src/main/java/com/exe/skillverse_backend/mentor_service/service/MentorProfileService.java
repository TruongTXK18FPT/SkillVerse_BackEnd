package com.exe.skillverse_backend.mentor_service.service;

import com.exe.skillverse_backend.mentor_service.dto.request.MentorProfileUpdateRequest;
import com.exe.skillverse_backend.mentor_service.dto.response.MentorProfileResponse;

public interface MentorProfileService {

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
    String uploadMentorAvatar(Long userId, byte[] fileData, String fileName);
}
