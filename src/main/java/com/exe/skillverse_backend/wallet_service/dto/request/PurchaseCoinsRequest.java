package com.exe.skillverse_backend.wallet_service.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO cho mua SkillCoin
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseCoinsRequest {
    
    /**
     * Số lượng Coin muốn mua
     */
    @NotNull(message = "Số lượng Coin không được để trống")
    @Min(value = 1, message = "Số lượng Coin tối thiểu là 1")
    @Max(value = 100000, message = "Số lượng Coin tối đa là 100,000")
    private Long coinAmount;
    
    /**
     * Phương thức thanh toán
     * - WALLET_CASH: Dùng tiền trong ví Cash
     * - PAYOS: Thanh toán trực tiếp qua PayOS
     */
    @NotBlank(message = "Phương thức thanh toán không được để trống")
    @Pattern(regexp = "WALLET_CASH|PAYOS", message = "Phương thức thanh toán không hợp lệ")
    private String paymentMethod;
    
    /**
     * ID gói Coin đã chọn (từ CoinWallet.tsx)
     * Ví dụ: "popular", "premium", "mega"
     */
    private String packageId;
    
    /**
     * URL trả về sau khi thanh toán (nếu dùng PayOS)
     */
    private String returnUrl;
    
    /**
     * URL trả về khi hủy (nếu dùng PayOS)
     */
    private String cancelUrl;
}
