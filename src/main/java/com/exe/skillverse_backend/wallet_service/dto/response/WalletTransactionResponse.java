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
    
    // User info for admin view
    private Long userId;
    private String userName;
    private String userEmail;
    private String userAvatarUrl;
    
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
        var user = transaction.getWallet().getUser();
        return WalletTransactionResponse.builder()
                .transactionId(transaction.getTransactionId())
                .walletId(transaction.getWallet().getWalletId())
                .userId(user != null ? user.getId() : null)
                .userName(user != null ? getUserDisplayName(user) : null)
                .userEmail(user != null ? user.getEmail() : null)
                .userAvatarUrl(user != null ? user.getAvatarUrl() : null)
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
    
    /**
     * Get display name for user - handles Google users who may have null firstName/lastName
     */
    private static String getUserDisplayName(com.exe.skillverse_backend.auth_service.entity.User user) {
        if (user == null) return null;
        
        String firstName = user.getFirstName();
        String lastName = user.getLastName();
        
        // If both are null or empty, use email as display name
        if ((firstName == null || firstName.isBlank()) && (lastName == null || lastName.isBlank())) {
            return user.getEmail() != null ? user.getEmail().split("@")[0] : "User";
        }
        
        // Build full name from available parts
        StringBuilder name = new StringBuilder();
        if (firstName != null && !firstName.isBlank()) {
            name.append(firstName);
        }
        if (lastName != null && !lastName.isBlank()) {
            if (name.length() > 0) name.append(" ");
            name.append(lastName);
        }
        
        return name.toString().trim();
    }
}
