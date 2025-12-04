package com.exe.skillverse_backend.wallet_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * WalletTransaction Entity - Lịch sử giao dịch ví
 * Ghi lại mọi thay đổi số dư trong ví (cả Cash và Coin)
 */
@Entity
@Table(name = "wallet_transactions", indexes = {
    @Index(name = "idx_transaction_wallet_id", columnList = "wallet_id"),
    @Index(name = "idx_transaction_type", columnList = "transaction_type"),
    @Index(name = "idx_transaction_created_at", columnList = "created_at"),
    @Index(name = "idx_transaction_reference_id", columnList = "reference_type, reference_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long transactionId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;
    
    /**
     * Loại giao dịch
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionType transactionType;
    
    /**
     * Loại tiền: CASH (VNĐ) hoặc COIN (SkillCoin)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CurrencyType currencyType;
    
    /**
     * Số tiền giao dịch (VNĐ) - dùng khi currencyType = CASH
     */
    @Column(precision = 15, scale = 2)
    private BigDecimal cashAmount;
    
    /**
     * Số Coin giao dịch - dùng khi currencyType = COIN
     */
    private Long coinAmount;
    
    /**
     * Số dư sau giao dịch (VNĐ) - chỉ dùng khi currencyType = CASH
     */
    @Column(precision = 15, scale = 2)
    private BigDecimal cashBalanceAfter;
    
    /**
     * Số dư Coin sau giao dịch - chỉ dùng khi currencyType = COIN
     */
    private Long coinBalanceAfter;
    
    /**
     * Mô tả giao dịch
     */
    @Column(nullable = false, length = 500)
    private String description;
    
    /**
     * Ghi chú bổ sung
     */
    @Column(length = 1000)
    private String notes;
    
    /**
     * Tham chiếu đến giao dịch khác (PaymentTransaction, WithdrawalRequest, etc.)
     * Ví dụ: "PAYMENT", "WITHDRAWAL", "PURCHASE_COIN", "COURSE", "TIP"
     */
    @Column(length = 50)
    private String referenceType;
    
    /**
     * ID của bản ghi tham chiếu
     */
    @Column(length = 100)
    private String referenceId;
    
    /**
     * Metadata bổ sung (JSON)
     * Ví dụ: {"courseId": 123, "mentorId": 456}
     */
    @Column(columnDefinition = "TEXT")
    private String metadata;
    
    /**
     * Trạng thái giao dịch
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.COMPLETED;
    
    /**
     * Phí giao dịch (nếu có)
     */
    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal fee = BigDecimal.ZERO;
    
    /**
     * Địa chỉ IP thực hiện giao dịch
     */
    @Column(length = 50)
    private String ipAddress;
    
    /**
     * User Agent (trình duyệt/thiết bị)
     */
    @Column(length = 255)
    private String userAgent;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Thời gian xử lý giao dịch (nếu cần duyệt)
     */
    private LocalDateTime processedAt;
    
    // ==================== ENUMS ====================
    
    /**
     * Loại giao dịch ví
     */
    public enum TransactionType {
        // Cash transactions
        DEPOSIT_CASH("Nạp tiền vào ví"),                    // Nạp tiền từ PayOS
        WITHDRAWAL_CASH("Rút tiền về tài khoản"),            // Rút tiền về ngân hàng
        PURCHASE_COINS("Mua SkillCoin"),                     // Dùng Cash mua Coin
        REFUND_CASH("Hoàn tiền"),                            // Hoàn tiền khi hủy giao dịch
        MENTOR_BOOKING("Thu nhập từ booking mentor"),        // Thu nhập từ buổi mentoring
        
        // Coin transactions
        EARN_COINS("Kiếm Coin"),                             // Kiếm Coin từ học tập
        SPEND_COINS("Chi tiêu Coin"),                        // Chi tiêu Coin
        PURCHASE_COURSE("Mua khóa học"),                     // Mua khóa học bằng Coin
        PURCHASE_PREMIUM("Mua Premium"),                     // Mua gói Premium
        TIP_MENTOR("Tặng Coin cho Mentor"),                  // Gửi tip cho mentor
        RECEIVE_TIP("Nhận Coin từ người khác"),              // Nhận tip
        BONUS_COINS("Thưởng Coin"),                          // Nhận thưởng từ hệ thống
        REWARD_ACHIEVEMENT("Thưởng thành tích"),             // Thưởng khi hoàn thành achievement
        DAILY_LOGIN_BONUS("Thưởng đăng nhập hàng ngày"),     // Thưởng checkin
        REFUND_COINS("Hoàn Coin"),                           // Hoàn Coin khi hủy
        
        // Admin operations
        ADMIN_ADJUSTMENT("Điều chỉnh bởi Admin"),            // Admin điều chỉnh số dư
        SYSTEM_CORRECTION("Hiệu chỉnh hệ thống");            // Sửa lỗi hệ thống
        
        private final String displayName;
        
        TransactionType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    /**
     * Loại tiền tệ
     */
    public enum CurrencyType {
        CASH,  // Tiền thật (VNĐ)
        COIN   // Tiền ảo (SkillCoin)
    }
    
    /**
     * Trạng thái giao dịch
     */
    public enum TransactionStatus {
        PENDING,      // Đang chờ xử lý
        PROCESSING,   // Đang xử lý
        COMPLETED,    // Hoàn thành
        FAILED,       // Thất bại
        CANCELLED,    // Đã hủy
        REVERSED      // Đã đảo ngược (hoàn tiền)
    }
    
    // ==================== BUSINESS METHODS ====================
    
    /**
     * Kiểm tra giao dịch có liên quan đến Cash không
     */
    public boolean isCashTransaction() {
        return currencyType == CurrencyType.CASH;
    }
    
    /**
     * Kiểm tra giao dịch có liên quan đến Coin không
     */
    public boolean isCoinTransaction() {
        return currencyType == CurrencyType.COIN;
    }
    
    /**
     * Lấy số tiền dạng chuỗi để hiển thị
     */
    public String getAmountDisplay() {
        if (currencyType == CurrencyType.CASH && cashAmount != null) {
            return String.format("%,.0f VNĐ", cashAmount);
        } else if (currencyType == CurrencyType.COIN && coinAmount != null) {
            return String.format("%,d Coins", coinAmount);
        }
        return "0";
    }
    
    /**
     * Giao dịch có tăng số dư không (deposit, earn, receive)
     */
    public boolean isCredit() {
        return transactionType == TransactionType.DEPOSIT_CASH ||
               transactionType == TransactionType.MENTOR_BOOKING ||
               transactionType == TransactionType.EARN_COINS ||
               transactionType == TransactionType.RECEIVE_TIP ||
               transactionType == TransactionType.BONUS_COINS ||
               transactionType == TransactionType.REWARD_ACHIEVEMENT ||
               transactionType == TransactionType.DAILY_LOGIN_BONUS ||
               transactionType == TransactionType.REFUND_CASH ||
               transactionType == TransactionType.REFUND_COINS;
    }
    
    /**
     * Giao dịch có giảm số dư không (withdrawal, spend, purchase, tip)
     */
    public boolean isDebit() {
        return !isCredit() && status == TransactionStatus.COMPLETED;
    }
}
