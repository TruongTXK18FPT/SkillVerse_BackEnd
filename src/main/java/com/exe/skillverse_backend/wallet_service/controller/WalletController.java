package com.exe.skillverse_backend.wallet_service.controller;

import com.exe.skillverse_backend.wallet_service.dto.request.DepositRequest;
import com.exe.skillverse_backend.wallet_service.dto.request.PurchaseCoinsRequest;
import com.exe.skillverse_backend.wallet_service.dto.request.WithdrawalRequest;
import com.exe.skillverse_backend.wallet_service.dto.response.WalletResponse;
import com.exe.skillverse_backend.wallet_service.dto.response.WalletTransactionResponse;
import com.exe.skillverse_backend.wallet_service.dto.response.WithdrawalRequestResponse;
import com.exe.skillverse_backend.wallet_service.entity.Wallet;
import com.exe.skillverse_backend.wallet_service.service.CoinService;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import com.exe.skillverse_backend.wallet_service.service.WithdrawalService;
import com.exe.skillverse_backend.payment_service.dto.response.CreatePaymentResponse;
import com.exe.skillverse_backend.payment_service.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Wallet Controller - User-facing APIs
 */
@Slf4j
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "Wallet & Transaction Management")
public class WalletController {
    
    private final WalletService walletService;
    private final CoinService coinService;
    private final WithdrawalService withdrawalService;
    private final PaymentService paymentService;
    
    /**
     * Get current user's wallet info
     */
    @GetMapping("/my-wallet")
    @Operation(summary = "Get my wallet", description = "Retrieve current user's wallet information")
    public ResponseEntity<WalletResponse> getMyWallet(Authentication authentication) {
        Long userId = extractUserId(authentication);
        
        // ✅ FIX: Tự động tạo wallet nếu chưa có (thay vì throw exception)
        Wallet wallet = walletService.getOrCreateWallet(userId);
        WalletResponse response = WalletResponse.fromEntity(wallet);
        
        log.info("✅ User {} lấy thông tin ví thành công", userId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Create deposit request (PayOS)
     */
    @PostMapping("/deposit")
    @Operation(summary = "Deposit cash", description = "Create PayOS payment to deposit cash to wallet")
    public ResponseEntity<CreatePaymentResponse> depositCash(
            @Valid @RequestBody DepositRequest request,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        
        CreatePaymentResponse paymentResponse = walletService.createDepositPayment(
            userId,
            request.getAmount(),
            request.getPaymentMethod(),
            request.getReturnUrl(),
            request.getCancelUrl()
        );
        
        log.info("💰 User {} tạo yêu cầu nạp {} VNĐ", userId, request.getAmount());
        return ResponseEntity.ok(paymentResponse);
    }
    
    /**
     * Purchase coins with wallet cash
     */
    @PostMapping("/coins/purchase-with-cash")
    @Operation(summary = "Purchase coins with cash", description = "Buy coins using cash in wallet")
    public ResponseEntity<Map<String, Object>> purchaseCoinsWithCash(
            @Valid @RequestBody PurchaseCoinsRequest request,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        
        Map<String, Object> result = coinService.purchaseCoinsWithWalletCash(
            userId,
            request.getCoinAmount(),
            request.getPackageId()
        );
        
        log.info("🪙 User {} mua {} Coins bằng Cash", userId, request.getCoinAmount());
        return ResponseEntity.ok(result);
    }
    
    /**
     * Purchase coins with PayOS
     */
    @PostMapping("/coins/purchase-with-payos")
    @Operation(summary = "Purchase coins with PayOS", description = "Buy coins directly via PayOS payment")
    public ResponseEntity<CreatePaymentResponse> purchaseCoinsWithPayOS(
            @Valid @RequestBody PurchaseCoinsRequest request,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        
        CreatePaymentResponse paymentResponse = coinService.purchaseCoinsWithPayOS(
            userId,
            request.getCoinAmount(),
            request.getPackageId(),
            request.getReturnUrl(),
            request.getCancelUrl()
        );
        
        log.info("🪙 User {} tạo thanh toán mua {} Coins qua PayOS", userId, request.getCoinAmount());
        return ResponseEntity.ok(paymentResponse);
    }
    
    /**
     * Get coin packages
     */
    @GetMapping("/coins/packages")
    @Operation(summary = "Get coin packages", description = "Retrieve available coin packages with pricing")
    public ResponseEntity<List<Map<String, Object>>> getCoinPackages() {
        List<Map<String, Object>> packages = coinService.getCoinPackages();
        return ResponseEntity.ok(packages);
    }
    
    /**
     * Calculate coin price (for custom amounts)
     */
    @GetMapping("/coins/calculate-price")
    @Operation(summary = "Calculate coin price", description = "Calculate price for custom coin amount")
    public ResponseEntity<Map<String, Object>> calculateCoinPrice(
            @RequestParam Long coinAmount
    ) {
        BigDecimal price = coinService.calculateCoinPrice(coinAmount);
        
        return ResponseEntity.ok(Map.of(
            "coinAmount", coinAmount,
            "price", price,
            "pricePerCoin", CoinService.COIN_PRICE_VND
        ));
    }
    
    /**
     * Set/update transaction PIN
     */
    @PutMapping("/pin")
    @Operation(summary = "Set transaction PIN", description = "Create or update transaction PIN for withdrawals")
    public ResponseEntity<Map<String, String>> setTransactionPin(
            @RequestBody Map<String, String> request,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        String pin = request.get("newPin");
        
        if (pin == null || pin.length() != 6) {
            return ResponseEntity.badRequest()
                .body(Map.of("message", "PIN phải có đúng 6 chữ số"));
        }
        
        walletService.setTransactionPin(userId, pin);
        
        log.info("🔐 User {} đã set/update transaction PIN", userId);
        return ResponseEntity.ok(Map.of("message", "Thiết lập PIN thành công"));
    }
    
    /**
     * Update bank account info
     */
    @PutMapping("/bank-account")
    @Operation(summary = "Update bank account", description = "Update bank account for withdrawals")
    public ResponseEntity<Map<String, String>> updateBankAccount(
            @RequestBody Map<String, String> request,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        
        String bankName = request.get("bankName");
        String accountNumber = request.get("bankAccountNumber");
        String accountName = request.get("bankAccountName");
        
        walletService.updateBankAccount(userId, bankName, accountNumber, accountName);
        
        log.info("🏦 User {} đã cập nhật thông tin ngân hàng", userId);
        return ResponseEntity.ok(Map.of("message", "Cập nhật thông tin ngân hàng thành công"));
    }
    
    /**
     * Enable/disable 2FA
     */
    @PutMapping("/2fa")
    @Operation(summary = "Toggle 2FA", description = "Enable or disable 2FA for withdrawals")
    public ResponseEntity<Map<String, Object>> toggle2FA(
            @RequestBody Map<String, Boolean> request,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        Boolean enable = request.get("enable");
        
        walletService.toggle2FA(userId, enable != null && enable);
        
        log.info("🔒 User {} đã {} 2FA", userId, enable ? "bật" : "tắt");
        return ResponseEntity.ok(Map.of(
            "message", enable ? "Đã bật 2FA" : "Đã tắt 2FA",
            "require2FA", enable
        ));
    }
    
    /**
     * Get transaction history
     */
    @GetMapping("/transactions")
    @Operation(summary = "Get transaction history", description = "Retrieve wallet transaction history with pagination")
    public ResponseEntity<Page<WalletTransactionResponse>> getTransactions(
            Authentication authentication,
            Pageable pageable
    ) {
        Long userId = extractUserId(authentication);
        Page<WalletTransactionResponse> transactions = walletService.getTransactionHistory(userId, pageable);
        return ResponseEntity.ok(transactions);
    }
    
    /**
     * Get transaction detail
     */
    @GetMapping("/transactions/{id}")
    @Operation(summary = "Get transaction detail", description = "Retrieve specific transaction details")
    public ResponseEntity<WalletTransactionResponse> getTransactionDetail(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        WalletTransactionResponse transaction = walletService.getTransactionDetail(userId, id);
        return ResponseEntity.ok(transaction);
    }
    
    /**
     * Create withdrawal request
     */
    @PostMapping("/withdraw/request")
    @Operation(summary = "Create withdrawal request", description = "Request cash withdrawal (requires admin approval)")
    public ResponseEntity<WithdrawalRequestResponse> createWithdrawalRequest(
            @Valid @RequestBody WithdrawalRequest request,
            Authentication authentication,
            jakarta.servlet.http.HttpServletRequest httpRequest
    ) {
        Long userId = extractUserId(authentication);
        
        WithdrawalRequestResponse withdrawal = withdrawalService.createWithdrawalRequest(
            userId,
            request.getAmount(),
            request.getBankName(),
            request.getBankAccountNumber(),
            request.getBankAccountName(),
            request.getBankBranch(),
            null, // reason field
            request.getNotes(), // userNotes
            request.getTransactionPin(),
            request.getTwoFactorCode(),
            httpRequest.getRemoteAddr(), // IP address
            httpRequest.getHeader("User-Agent") // User agent
        );
        
        log.info("💸 User {} tạo yêu cầu rút {} VNĐ", userId, request.getAmount());
        return ResponseEntity.ok(withdrawal);
    }
    
    /**
     * Get my withdrawal requests
     */
    @GetMapping("/withdraw/my-requests")
    @Operation(summary = "Get my withdrawal requests", description = "Retrieve current user's withdrawal request history")
    public ResponseEntity<Page<WithdrawalRequestResponse>> getMyWithdrawalRequests(
            Authentication authentication,
            Pageable pageable
    ) {
        Long userId = extractUserId(authentication);
        Page<WithdrawalRequestResponse> requests = withdrawalService.getMyWithdrawalRequests(userId, pageable);
        return ResponseEntity.ok(requests);
    }
    
    /**
     * Get withdrawal request detail
     */
    @GetMapping("/withdraw/{id}")
    @Operation(summary = "Get withdrawal detail", description = "Retrieve specific withdrawal request details")
    public ResponseEntity<WithdrawalRequestResponse> getWithdrawalDetail(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        WithdrawalRequestResponse withdrawal = withdrawalService.getWithdrawalRequestDetail(userId, id);
        return ResponseEntity.ok(withdrawal);
    }
    
    /**
     * Cancel withdrawal request
     */
    @PutMapping("/withdraw/{id}/cancel")
    @Operation(summary = "Cancel withdrawal request", description = "Cancel pending or approved withdrawal request")
    public ResponseEntity<WithdrawalRequestResponse> cancelWithdrawalRequest(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> request,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        String reason = request != null ? request.get("reason") : null;
        
        WithdrawalRequestResponse withdrawal = withdrawalService.cancelWithdrawalRequest(id, userId, reason);
        
        log.info("❌ User {} đã hủy withdrawal request {}", userId, id);
        return ResponseEntity.ok(withdrawal);
    }
    
    /**
     * Get wallet statistics
     */
    @GetMapping("/statistics")
    @Operation(summary = "Get wallet statistics", description = "Retrieve personal wallet statistics")
    public ResponseEntity<Map<String, Object>> getWalletStatistics(
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        Map<String, Object> statistics = walletService.getWalletStatistics(userId);
        return ResponseEntity.ok(statistics);
    }
    
    /**
     * Download invoice PDF for a wallet transaction
     */
    @GetMapping("/transactions/{id}/invoice")
    @Operation(summary = "Download transaction invoice", 
               description = "Generate and download PDF invoice for a wallet transaction")
    public ResponseEntity<byte[]> downloadTransactionInvoice(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        log.info("📄 User {} downloading invoice for transaction {}", userId, id);
        
        // Verify the transaction belongs to this user through wallet ownership check in service
        byte[] pdfBytes = paymentService.generateWalletTransactionInvoicePdf(id);
        
        String filename = "invoice-" + id + ".pdf";
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdfBytes);
    }
    
    // ==================== HELPER METHODS ====================
    
    private Long extractUserId(Authentication authentication) {
        // TODO: Extract user ID from JWT token
        // For now, assume user ID is in authentication principal
        return Long.parseLong(authentication.getName());
    }
}
