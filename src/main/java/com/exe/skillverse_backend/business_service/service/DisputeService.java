package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.business_service.entity.Dispute;
import com.exe.skillverse_backend.business_service.entity.DisputeEvidence;
import com.exe.skillverse_backend.business_service.entity.DisputeResponseEntity;
import com.exe.skillverse_backend.business_service.dto.request.OpenDisputeRequest;
import com.exe.skillverse_backend.business_service.dto.request.SubmitEvidenceRequest;
import com.exe.skillverse_backend.business_service.dto.request.ResolveDisputeRequest;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DisputeService {
    Dispute openDispute(Long userId, OpenDisputeRequest request);
    DisputeEvidence submitEvidence(Long userId, Long disputeId, SubmitEvidenceRequest request);
    DisputeResponseEntity respondToEvidence(Long userId, Long disputeId, Long evidenceId, String response);
    Dispute resolveDispute(Long adminId, Long disputeId, ResolveDisputeRequest request);
    Dispute getDispute(Long disputeId);
    List<Dispute> getDisputesByJob(Long jobId);
    Page<Dispute> getMyDisputes(Long userId, Pageable pageable);
    Page<Dispute> getAllDisputes(Pageable pageable);
    List<DisputeEvidence> getDisputeEvidence(Long disputeId);
}
