package com.exe.skillverse_backend.wallet_service.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO cho yêu cầu rút tiền
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WithdrawalRequestDTO {
    
    /**
     * Số tiền muốn rút (VNĐ)
     * Tối thiểu 100,000 VNĐ, tối đa 100,000,000 VNĐ
     */
    @NotNull(message = "Số tiền rút không được để trống")
    @DecimalMin(value = "100000", message = "Số tiền rút tối thiểu là 100,000 VNĐ")
    @DecimalMax(value = "100000000", message = "Số tiền rút tối đa là 100,000,000 VNĐ")
    private BigDecimal amount;
    
    /**
     * Tên ngân hàng
     */
    @NotBlank(message = "Tên ngân hàng không được để trống")
    @Size(max = 50, message = "Tên ngân hàng không quá 50 ký tự")
    private String bankName;
    
    /**
     * Số tài khoản ngân hàng
     */
    @NotBlank(message = "Số tài khoản không được để trống")
    @Pattern(regexp = "^[0-9]{9,16}$", message = "Số tài khoản phải từ 9-16 chữ số")
    private String bankAccountNumber;
    
    /**
     * Tên chủ tài khoản
     */
    @NotBlank(message = "Tên chủ tài khoản không được để trống")
    @Size(max = 100, message = "Tên chủ tài khoản không quá 100 ký tự")
    private String bankAccountName;
    
    /**
     * Chi nhánh ngân hàng (optional)
     */
    @Size(max = 100, message = "Tên chi nhánh không quá 100 ký tự")
    private String bankBranch;
    
    /**
     * Lý do rút tiền
     */
    @Size(max = 500, message = "Lý do không quá 500 ký tự")
    private String reason;
    
    /**
     * Ghi chú
     */
    @Size(max = 1000, message = "Ghi chú không quá 1000 ký tự")
    private String notes;
    
    /**
     * Mã PIN giao dịch (6 chữ số)
     */
    @NotBlank(message = "Mã PIN không được để trống")
    @Pattern(regexp = "^[0-9]{6}$", message = "Mã PIN phải là 6 chữ số")
    private String transactionPin;
    
    /**
     * Mã xác thực 2FA (nếu bật 2FA)
     */
    @Pattern(regexp = "^[0-9]{6}$", message = "Mã 2FA phải là 6 chữ số")
    private String twoFACode;
}
