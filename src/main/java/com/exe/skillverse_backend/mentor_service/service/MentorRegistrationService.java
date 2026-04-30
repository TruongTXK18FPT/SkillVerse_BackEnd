package com.exe.skillverse_backend.mentor_service.service;

import com.exe.skillverse_backend.mentor_service.dto.request.MentorRegistrationRequest;
import com.exe.skillverse_backend.mentor_service.dto.response.MentorRegistrationResponse;
import com.exe.skillverse_backend.shared.service.RegistrationService;
import org.springframework.web.multipart.MultipartFile;

public interface MentorRegistrationService extends RegistrationService<MentorRegistrationRequest, MentorRegistrationResponse> {
    MentorRegistrationResponse registerMentor(
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
            Boolean mergeCertificates,
            MultipartFile cccdFrontFile,
            MultipartFile cccdBackFile
    );
}