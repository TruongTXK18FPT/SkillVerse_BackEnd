package com.exe.skillverse_backend.wallet_service.dto.response;

import com.exe.skillverse_backend.wallet_service.entity.WithdrawalRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO cho yêu cầu rút tiền
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WithdrawalRequestResponse {
    
    private Long requestId;
    private String requestCode;
    
    private Long userId;
    private String userFullName;
    private String userEmail;
    
    private BigDecimal amount;
    private BigDecimal fee;
    private BigDecimal netAmount;
    
    private String status;
    private String statusDisplayName;
    private String statusDescription;
    
    private String bankName;
    private String bankAccountNumber;
    private String bankAccountName;
    private String bankBranch;
    
    private String reason;
    private String userNotes;
    
    private Boolean pinVerified;
    private Boolean twoFAVerified;
    
    private Long approvedByUserId;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private String adminNotes;
    private String rejectionReason;
    
    private String bankTransactionId;
    private LocalDateTime completedAt;
    
    private Integer priority;
    private Integer retryCount;
    private String errorMessage;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime expiresAt;
    
    private Boolean canCancel;
    private Boolean isExpired;
    
    /**
     * Convert from entity to DTO
     */
    public static WithdrawalRequestResponse fromEntity(WithdrawalRequest request) {
        String userFullName = buildFullName(request.getUser().getFirstName(), request.getUser().getLastName());
        String approvedByName = request.getApprovedBy() != null
            ? buildFullName(request.getApprovedBy().getFirstName(), request.getApprovedBy().getLastName())
            : null;
            
        return WithdrawalRequestResponse.builder()
                .requestId(request.getRequestId())
                .requestCode(request.getRequestCode())
                .userId(request.getUser().getId())
                .userFullName(userFullName)
                .userEmail(request.getUser().getEmail())
                .amount(request.getAmount())
                .fee(request.getFee())
                .netAmount(request.getNetAmount())
                .status(request.getStatus().name())
                .statusDisplayName(request.getStatus().getDisplayName())
                .statusDescription(request.getStatus().getDescription())
                .bankName(request.getBankName())
                .bankAccountNumber(maskAccountNumber(request.getBankAccountNumber()))
                .bankAccountName(request.getBankAccountName())
                .bankBranch(request.getBankBranch())
                .reason(request.getReason())
                .userNotes(request.getUserNotes())
                .pinVerified(request.getPinVerified())
                .twoFAVerified(request.getTwoFAVerified())
                .approvedByUserId(request.getApprovedBy() != null ? request.getApprovedBy().getId() : null)
                .approvedByName(approvedByName)
                .approvedAt(request.getApprovedAt())
                .adminNotes(request.getAdminNotes())
                .rejectionReason(request.getRejectionReason())
                .bankTransactionId(request.getBankTransactionId())
                .completedAt(request.getCompletedAt())
                .priority(request.getPriority())
                .retryCount(request.getRetryCount())
                .errorMessage(request.getErrorMessage())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .expiresAt(request.getExpiresAt())
                .canCancel(request.getStatus().canCancel())
                .isExpired(request.isExpired())
                .build();
    }
    
    /**
     * Convert for admin (full account number)
     */
    public static WithdrawalRequestResponse fromEntityForAdmin(WithdrawalRequest request) {
        WithdrawalRequestResponse response = fromEntity(request);
        response.setBankAccountNumber(request.getBankAccountNumber()); // Full number for admin
        return response;
    }
    
    /**
     * Mask account number for security
     */
    private static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return accountNumber;
        }
        int visibleDigits = 4;
        int maskedLength = accountNumber.length() - visibleDigits;
        return "*".repeat(maskedLength) + accountNumber.substring(maskedLength);
    }

    /**
     * Safely build full name, handling null firstName and lastName
     */
    private static String buildFullName(String firstName, String lastName) {
        String first = firstName != null ? firstName.trim() : "";
        String last = lastName != null ? lastName.trim() : "";

        if (first.isEmpty() && last.isEmpty()) {
            return "Unknown User";
        }

        if (first.isEmpty()) {
            return last;
        }

        if (last.isEmpty()) {
            return first;
        }

        return first + " " + last;
    }
}
