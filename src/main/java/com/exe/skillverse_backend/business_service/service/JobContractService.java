package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.business_service.dto.request.CreateContractRequest;
import com.exe.skillverse_backend.business_service.dto.request.OnboardingInfoRequest;
import com.exe.skillverse_backend.business_service.dto.request.SignContractRequest;
import com.exe.skillverse_backend.business_service.dto.request.UpdateContractRequest;
import com.exe.skillverse_backend.business_service.dto.response.ContractSignatureResponse;
import com.exe.skillverse_backend.business_service.dto.response.JobContractResponse;
import com.exe.skillverse_backend.business_service.dto.response.OnboardingInfoResponse;
import com.exe.skillverse_backend.identity_verification_service.dto.IdCardExtractionResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface JobContractService {
    JobContractResponse createContract(CreateContractRequest request, Long userId);
    JobContractResponse sendForSignature(Long contractId, Long userId);
    JobContractResponse signContract(Long contractId, SignContractRequest request, Long userId);
    JobContractResponse rejectContract(Long contractId, String reason, Long userId);
    JobContractResponse cancelContract(Long contractId, Long userId);
    JobContractResponse updateContract(Long contractId, UpdateContractRequest request, Long userId);
    JobContractResponse getContractById(Long id);
    JobContractResponse getContractByApplication(Long applicationId);
    List<JobContractResponse> getMyContracts(String role, Long userId);

    // ==================== ONBOARDING & OCR ====================

    /** Extract CCCD info via FPT AI OCR — image is NOT stored */
    IdCardExtractionResult extractIdCardForApplication(Long applicationId, MultipartFile image, Long userId);

    /** Submit onboarding info (CCCD text + bank) and transition to AWAITING_ONBOARDING_INFO */
    OnboardingInfoResponse submitOnboardingInfo(Long applicationId, OnboardingInfoRequest request, Long userId);

    /** Get onboarding info for an application */
    OnboardingInfoResponse getOnboardingInfo(Long applicationId, Long userId);

    /** Get the most recently submitted onboarding info for the user to reuse */
    OnboardingInfoResponse getLatestOnboardingInfo(Long candidateUserId);

    void remindOnboardingInfo(Long applicationId, Long recruiterUserId);

    /** Upload custom contract PDF to Cloudinary */
    JobContractResponse uploadContractPdf(Long contractId, MultipartFile file, java.time.LocalDate startDate, java.time.LocalDate endDate, Long userId);
}
