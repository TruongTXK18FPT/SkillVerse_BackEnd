package com.exe.skillverse_backend.wallet_service.dto.response;

import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO cho giao dịch ví
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransactionResponse {
    
    private Long transactionId;
    private Long walletId;
    
    private String transactionType;
    private String transactionTypeName;
    private String currencyType;
    
    private BigDecimal cashAmount;
    private Long coinAmount;
    
    private BigDecimal cashBalanceAfter;
    private Long coinBalanceAfter;
    
    private String description;
    private String notes;
    
    private String referenceType;
    private String referenceId;
    
    private String status;
    private BigDecimal fee;
    
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    
    private Boolean isCredit; // Tăng số dư
    private Boolean isDebit;  // Giảm số dư
    
    /**
     * Convert from entity to DTO
     */
    public static WalletTransactionResponse fromEntity(WalletTransaction transaction) {
        return WalletTransactionResponse.builder()
                .transactionId(transaction.getTransactionId())
                .walletId(transaction.getWallet().getWalletId())
                .transactionType(transaction.getTransactionType().name())
                .transactionTypeName(transaction.getTransactionType().getDisplayName())
                .currencyType(transaction.getCurrencyType().name())
                .cashAmount(transaction.getCashAmount())
                .coinAmount(transaction.getCoinAmount())
                .cashBalanceAfter(transaction.getCashBalanceAfter())
                .coinBalanceAfter(transaction.getCoinBalanceAfter())
                .description(transaction.getDescription())
                .notes(transaction.getNotes())
                .referenceType(transaction.getReferenceType())
                .referenceId(transaction.getReferenceId())
                .status(transaction.getStatus().name())
                .fee(transaction.getFee())
                .createdAt(transaction.getCreatedAt())
                .processedAt(transaction.getProcessedAt())
                .isCredit(transaction.isCredit())
                .isDebit(transaction.isDebit())
                .build();
    }
}
