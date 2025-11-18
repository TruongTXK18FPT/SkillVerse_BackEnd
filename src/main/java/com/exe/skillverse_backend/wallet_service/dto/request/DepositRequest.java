package com.exe.skillverse_backend.wallet_service.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO cho nạp tiền vào ví
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepositRequest {
    
    /**
     * Số tiền nạp (VNĐ)
     * Tối thiểu 10,000 VNĐ, tối đa 50,000,000 VNĐ
     */
    @NotNull(message = "Số tiền nạp không được để trống")
    @DecimalMin(value = "10000", message = "Số tiền nạp tối thiểu là 10,000 VNĐ")
    @DecimalMax(value = "50000000", message = "Số tiền nạp tối đa là 50,000,000 VNĐ")
    private BigDecimal amount;
    
    /**
     * Phương thức thanh toán (PayOS)
     */
    @NotBlank(message = "Phương thức thanh toán không được để trống")
    private String paymentMethod;
    
    /**
     * URL trả về sau khi thanh toán thành công
     */
    private String returnUrl;
    
    /**
     * URL trả về khi hủy thanh toán
     */
    private String cancelUrl;
    
    /**
     * Mô tả giao dịch
     */
    private String description;
}
