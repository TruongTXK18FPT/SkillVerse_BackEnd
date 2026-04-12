package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.business_service.dto.request.RecruiterProfileUpdateRequest;
import com.exe.skillverse_backend.business_service.dto.response.RecruiterProfileResponse;
import org.springframework.web.multipart.MultipartFile;

public interface RecruiterProfileService {

    RecruiterProfileResponse getRecruiterProfile(Long userId);

    RecruiterProfileResponse updateRecruiterProfile(Long userId, RecruiterProfileUpdateRequest request);

    String uploadCompanyLogo(Long userId, MultipartFile file);
}
