package com.exe.skillverse_backend.wallet_service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for creating withdrawal request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Withdrawal request creation")
public class WithdrawalRequest {
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "100000", message = "Minimum withdrawal amount is 100,000 VNĐ")
    @DecimalMax(value = "100000000", message = "Maximum withdrawal amount is 100,000,000 VNĐ")
    @Schema(description = "Withdrawal amount in VNĐ", example = "500000")
    private BigDecimal amount;
    
    @NotBlank(message = "Bank name is required")
    @Schema(description = "Bank name", example = "Vietcombank")
    private String bankName;
    
    @NotBlank(message = "Bank account number is required")
    @Pattern(regexp = "^[A-Za-z0-9]{5,19}$", message = "Bank account number must be 5-19 alphanumeric characters")
    @Schema(description = "Bank account number (alphanumeric, 5-19 chars)", example = "1234567890")
    private String bankAccountNumber;
    
    @NotBlank(message = "Bank account name is required")
    @Schema(description = "Bank account holder name", example = "NGUYEN VAN A")
    private String bankAccountName;
    
    @Schema(description = "Bank branch (optional)", example = "Chi nhánh Hà Nội")
    private String bankBranch;
    
    @NotBlank(message = "Transaction PIN is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "Transaction PIN must be 6 digits")
    @Schema(description = "6-digit transaction PIN", example = "123456")
    private String transactionPin;
    
    @Schema(description = "2FA code (if enabled)", example = "654321")
    private String twoFactorCode;
    
    @Schema(description = "Optional notes from user")
    private String notes;
}
