package com.exe.skillverse_backend.admin_service.dto.request;

import com.exe.skillverse_backend.business_service.entity.Dispute.DisputeResolution;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveDisputeAdminRequest {
    private DisputeResolution resolution;
    private BigDecimal partialRefundPct;
    private String resolutionNotes;

    // Additional resolution details
    private String winner; // "WORKER" or "RECRUITER" for clarity
    private Boolean escrowReleasedToWorker;
    private Boolean escrowRefundedToRecruiter;
}
