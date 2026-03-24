package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.business_service.entity.EscrowTransaction;
import com.exe.skillverse_backend.business_service.entity.JobEscrow;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EscrowService {
    JobEscrow fundEscrow(Long jobId, Long userId);
    JobEscrow releaseEscrow(Long jobId, Long userId, String message);
    JobEscrow refundEscrow(Long jobId, Long userId, String reason);
    JobEscrow getEscrowByJobId(Long jobId);
    List<EscrowTransaction> getEscrowTransactions(Long jobId);
    Page<EscrowTransaction> getEscrowTransactionsPaged(Long jobId, Pageable pageable);
    void releasePendingPayouts();
}
