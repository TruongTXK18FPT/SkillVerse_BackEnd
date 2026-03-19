package com.exe.skillverse_backend.wallet_service.service;

import com.exe.skillverse_backend.wallet_service.dto.response.WithdrawalRequestResponse;
import com.exe.skillverse_backend.wallet_service.entity.WithdrawalRequest;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WithdrawalService {
    
    WithdrawalRequestResponse createWithdrawalRequest(
            Long userId,
            BigDecimal amount,
            String bankName,
            String bankAccountNumber,
            String bankAccountName,
            String bankBranch,
            String reason,
            String userNotes,
            String transactionPin,
            String twoFACode,
            String ipAddress,
            String userAgent);
            
    WithdrawalRequestResponse approveWithdrawalRequest(
            Long requestId,
            Long adminId,
            String adminNotes);
            
    WithdrawalRequestResponse rejectWithdrawalRequest(
            Long requestId,
            Long adminId,
            String rejectionReason);
            
    WithdrawalRequestResponse completeWithdrawal(
            Long requestId,
            Long adminId,
            String bankTransactionId);
            
    WithdrawalRequestResponse cancelWithdrawalRequest(Long requestId, Long userId);
    
    WithdrawalRequestResponse cancelWithdrawalRequest(
            Long requestId,
            Long userId,
            String reason);
            
    Page<WithdrawalRequestResponse> getMyWithdrawalRequests(Long userId, Pageable pageable);
    
    WithdrawalRequestResponse getWithdrawalRequest(Long requestId, Long userId);
    
    Page<WithdrawalRequestResponse> getAllWithdrawalRequests(
            WithdrawalRequest.WithdrawalStatus status,
            Pageable pageable);
            
    Page<WithdrawalRequestResponse> getPendingRequests(Pageable pageable);
    
    WithdrawalRequestResponse getWithdrawalRequestForAdmin(Long requestId);
    
    WithdrawalRequestResponse getWithdrawalRequestDetailAdmin(Long requestId);
    
    WithdrawalRequestResponse getWithdrawalRequestDetail(Long userId, Long requestId);
    
    void processExpiredRequests();
}
