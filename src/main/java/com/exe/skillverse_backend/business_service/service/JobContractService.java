package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.business_service.dto.request.CreateContractRequest;
import com.exe.skillverse_backend.business_service.dto.request.SignContractRequest;
import com.exe.skillverse_backend.business_service.dto.request.UpdateContractRequest;
import com.exe.skillverse_backend.business_service.dto.response.ContractSignatureResponse;
import com.exe.skillverse_backend.business_service.dto.response.JobContractResponse;

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
}
