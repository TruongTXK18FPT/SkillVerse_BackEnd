package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.business_service.dto.request.BusinessRegistrationRequest;
import com.exe.skillverse_backend.business_service.dto.response.BusinessRegistrationResponse;
import com.exe.skillverse_backend.shared.service.RegistrationService;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface BusinessRegistrationService extends RegistrationService<BusinessRegistrationRequest, BusinessRegistrationResponse> {

    BusinessRegistrationResponse registerBusiness(
            String email,
            String password,
            String confirmPassword,
            String fullName,
            String phone,
            String bio,
            String address,
            String region,
            String companyName,
            String companyWebsite,
            String companyAddress,
            String taxCodeOrBusinessRegistrationNumber,
            String contactPersonPhone,
            String contactPersonPosition,
            String companySize,
            String industry,
            MultipartFile companyDocumentsFile,
            List<MultipartFile> companyDocumentsFiles);
}
