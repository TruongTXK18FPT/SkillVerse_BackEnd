package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.business_service.dto.request.RecruiterProfileUpdateRequest;
import com.exe.skillverse_backend.business_service.dto.response.RecruiterProfileResponse;

public interface RecruiterProfileService {

    RecruiterProfileResponse getRecruiterProfile(Long userId);

    RecruiterProfileResponse updateRecruiterProfile(Long userId, RecruiterProfileUpdateRequest request);
}
