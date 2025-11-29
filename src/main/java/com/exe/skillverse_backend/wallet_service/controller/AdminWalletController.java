package com.exe.skillverse_backend.wallet_service.controller;

import com.exe.skillverse_backend.wallet_service.dto.response.WalletResponse;
import com.exe.skillverse_backend.wallet_service.dto.response.WalletTransactionResponse;
import com.exe.skillverse_backend.wallet_service.dto.response.WithdrawalRequestResponse;
import com.exe.skillverse_backend.wallet_service.entity.WithdrawalRequest;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import com.exe.skillverse_backend.wallet_service.service.WithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin Wallet Controller - Admin-only APIs for wallet management
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/wallet")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Wallet", description = "Admin wallet & withdrawal management")
public class AdminWalletController {
    
    private final WalletService walletService;
    private final WithdrawalService withdrawalService;
    
    /**
     * Get all withdrawal requests with filtering
     */
    @GetMapping("/withdrawals")
    @Operation(summary = "Get all withdrawal requests", description = "Retrieve all withdrawal requests with optional status filter")
    public ResponseEntity<Page<WithdrawalRequestResponse>> getAllWithdrawals(
            @RequestParam(required = false) WithdrawalRequest.WithdrawalStatus status,
            Pageable pageable
    ) {
        Page<WithdrawalRequestResponse> requests = withdrawalService.getAllWithdrawalRequests(status, pageable);
        return ResponseEntity.ok(requests);
    }
    
    /**
     * Get pending withdrawal requests (priority queue)
     */
    @GetMapping("/withdrawals/pending")
    @Operation(summary = "Get pending withdrawals", description = "Retrieve pending withdrawal requests ordered by priority")
    public ResponseEntity<Page<WithdrawalRequestResponse>> getPendingWithdrawals(
            Pageable pageable
    ) {
        Page<WithdrawalRequestResponse> requests = withdrawalService.getPendingRequests(pageable);
        return ResponseEntity.ok(requests);
    }
    
    /**
     * Get withdrawal request detail (full admin view)
     */
    @GetMapping("/withdrawals/{id}")
    @Operation(summary = "Get withdrawal detail", description = "Retrieve full withdrawal request details (admin view)")
    public ResponseEntity<WithdrawalRequestResponse> getWithdrawalDetail(
            @PathVariable Long id
    ) {
        WithdrawalRequestResponse withdrawal = withdrawalService.getWithdrawalRequestDetailAdmin(id);
        return ResponseEntity.ok(withdrawal);
    }
    
    /**
     * Approve withdrawal request and complete withdrawal
     * This will immediately deduct balance and create transaction
     */
    @PutMapping("/withdrawals/{id}/approve")
    @Operation(summary = "Approve and complete withdrawal", description = "Approve pending withdrawal request and immediately deduct balance")
    public ResponseEntity<WithdrawalRequestResponse> approveWithdrawal(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> request,
            Authentication authentication
    ) {
        Long adminId = extractUserId(authentication);
        String notes = request != null ? request.get("notes") : null;
        
        WithdrawalRequestResponse withdrawal = withdrawalService.approveWithdrawalRequest(id, adminId, notes);
        
        log.info("✅ Admin {} đã duyệt withdrawal request {}", adminId, id);
        return ResponseEntity.ok(withdrawal);
    }
    
    /**
     * Reject withdrawal request
     */
    @PutMapping("/withdrawals/{id}/reject")
    @Operation(summary = "Reject withdrawal", description = "Reject pending withdrawal request with reason")
    public ResponseEntity<WithdrawalRequestResponse> rejectWithdrawal(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            Authentication authentication
    ) {
        Long adminId = extractUserId(authentication);
        String reason = request.get("reason");
        
        if (reason == null || reason.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        
        WithdrawalRequestResponse withdrawal = withdrawalService.rejectWithdrawalRequest(id, adminId, reason);
        
        log.info("❌ Admin {} đã từ chối withdrawal request {} - Lý do: {}", adminId, id, reason);
        return ResponseEntity.ok(withdrawal);
    }
    
    /**
     * Update bank transaction ID (optional)
     * Balance has already been deducted during approval
     */
    @PutMapping("/withdrawals/{id}/complete")
    @Operation(summary = "Update bank transaction ID", description = "Update bank transaction ID for completed withdrawal (optional)")
    public ResponseEntity<WithdrawalRequestResponse> completeWithdrawal(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            Authentication authentication
    ) {
        Long adminId = extractUserId(authentication);
        String bankTransactionId = request.get("bankTransactionId");
        
        if (bankTransactionId == null || bankTransactionId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        
        WithdrawalRequestResponse withdrawal = withdrawalService.completeWithdrawal(id, adminId, bankTransactionId);
        
        log.info("💳 Admin {} đã cập nhật bank transaction ID cho withdrawal {} - Bank TX: {}", adminId, id, bankTransactionId);
        return ResponseEntity.ok(withdrawal);
    }
    
    /**
     * Get user's wallet (admin view)
     */
    @GetMapping("/users/{userId}/wallet")
    @Operation(summary = "Get user wallet", description = "View any user's wallet information")
    public ResponseEntity<WalletResponse> getUserWallet(
            @PathVariable Long userId
    ) {
        WalletResponse wallet = walletService.getWalletByUserId(userId);
        return ResponseEntity.ok(wallet);
    }
    
    /**
     * Get user's transaction history (admin view)
     */
    @GetMapping("/users/{userId}/transactions")
    @Operation(summary = "Get user transactions", description = "View user's transaction history")
    public ResponseEntity<Page<WalletTransactionResponse>> getUserTransactions(
            @PathVariable Long userId,
            Pageable pageable
    ) {
        Page<WalletTransactionResponse> transactions = walletService.getTransactionHistory(userId, pageable);
        return ResponseEntity.ok(transactions);
    }
    
    /**
     * Get ALL wallet transactions (admin view for transaction management)
     */
    @GetMapping("/transactions")
    @Operation(summary = "Get all transactions", description = "View all wallet transactions system-wide")
    public ResponseEntity<Page<WalletTransactionResponse>> getAllTransactions(
            @RequestParam(required = false) String type,
            Pageable pageable
    ) {
        Page<WalletTransactionResponse> transactions = walletService.getAllTransactionsAdmin(type, pageable);
        return ResponseEntity.ok(transactions);
    }
    
    /**
     * Get global wallet statistics
     */
    @GetMapping("/statistics/overview")
    @Operation(summary = "Get global statistics", description = "Retrieve global wallet system statistics")
    public ResponseEntity<Map<String, Object>> getGlobalStatistics() {
        Map<String, Object> statistics = walletService.getGlobalStatistics();
        return ResponseEntity.ok(statistics);
    }
    
    /**
     * Get daily deposit/withdrawal statistics
     */
    @GetMapping("/statistics/daily")
    @Operation(summary = "Get daily statistics", description = "Retrieve daily deposit and withdrawal statistics")
    public ResponseEntity<Map<String, Object>> getDailyStatistics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        Map<String, Object> statistics = walletService.getDailyStatistics(startDate, endDate);
        return ResponseEntity.ok(statistics);
    }
    
    /**
     * Manually process expired withdrawal requests
     */
    @PostMapping("/withdrawals/process-expired")
    @Operation(summary = "Process expired requests", description = "Manually trigger processing of expired withdrawal requests")
    public ResponseEntity<Map<String, String>> processExpiredRequests() {
        withdrawalService.processExpiredRequests();
        log.info("🔄 Admin đã trigger xử lý withdrawal requests hết hạn");
        return ResponseEntity.ok(Map.of("message", "Đã xử lý các yêu cầu hết hạn"));
    }
    
    // ==================== HELPER METHODS ====================
    
    private Long extractUserId(Authentication authentication) {
        // TODO: Extract user ID from JWT token
        // For now, assume user ID is in authentication principal
        return Long.parseLong(authentication.getName());
    }
}
