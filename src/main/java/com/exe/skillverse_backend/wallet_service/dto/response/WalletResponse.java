package com.exe.skillverse_backend.wallet_service.dto.response;

import com.exe.skillverse_backend.wallet_service.entity.Wallet;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO cho thông tin ví
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletResponse {
    
    private Long walletId;
    private Long userId;
    
    // Số dư
    private BigDecimal cashBalance;              // Tiền thật có thể rút
    private Long coinBalance;                    // Tiền ảo
    private BigDecimal frozenCashBalance;        // Tiền đang đóng băng
    private BigDecimal availableCashBalance;     // Tiền khả dụng = cashBalance - frozenCashBalance
    
    // Thống kê
    private BigDecimal totalDeposited;
    private BigDecimal totalWithdrawn;
    private Long totalCoinsEarned;
    private Long totalCoinsSpent;
    
    // Thông tin ngân hàng
    private String bankName;
    private String bankAccountNumber;
    private String bankAccountName;
    
    // Trạng thái
    private String status;
    private Boolean hasBankAccount;
    private Boolean hasTransactionPin;
    private Boolean require2FA;
    
    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime lastTransactionAt;
    
    /**
     * Convert từ Entity sang DTO
     */
    public static WalletResponse fromEntity(Wallet wallet) {
        return WalletResponse.builder()
                .walletId(wallet.getWalletId())
                .userId(wallet.getUser().getId())
                .cashBalance(wallet.getCashBalance())
                .coinBalance(wallet.getCoinBalance())
                .frozenCashBalance(wallet.getFrozenCashBalance())
                .availableCashBalance(wallet.getAvailableCashBalance())
                .totalDeposited(wallet.getTotalDeposited())
                .totalWithdrawn(wallet.getTotalWithdrawn())
                .totalCoinsEarned(wallet.getTotalCoinsEarned())
                .totalCoinsSpent(wallet.getTotalCoinsSpent())
                .bankName(wallet.getBankName())
                .bankAccountNumber(maskAccountNumber(wallet.getBankAccountNumber()))
                .bankAccountName(wallet.getBankAccountName())
                .status(wallet.getStatus().name())
                .hasBankAccount(wallet.getBankAccountNumber() != null)
                .hasTransactionPin(wallet.getTransactionPin() != null)
                .require2FA(wallet.getRequire2FA())
                .createdAt(wallet.getCreatedAt())
                .lastTransactionAt(wallet.getLastTransactionAt())
                .build();
    }
    
    /**
     * Che số tài khoản ngân hàng (chỉ hiện 4 số cuối)
     */
    private static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return accountNumber;
        }
        int visibleDigits = 4;
        int maskedLength = accountNumber.length() - visibleDigits;
        return "*".repeat(maskedLength) + accountNumber.substring(maskedLength);
    }
}
