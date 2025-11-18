package com.exe.skillverse_backend.wallet_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * WithdrawalRequest Entity - Yêu cầu rút tiền từ ví
 * User tạo yêu cầu → Admin duyệt → Chuyển tiền → Hoàn tất
 */
@Entity
@Table(name = "withdrawal_requests", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_request_code", columnList = "request_code", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WithdrawalRequest {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long requestId;
    
    /**
     * Mã yêu cầu duy nhất (WD-YYYYMMDD-XXXX)
     */
    @Column(nullable = false, unique = true, length = 50)
    private String requestCode;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;
    
    /**
     * Số tiền yêu cầu rút (VNĐ)
     */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    
    /**
     * Phí rút tiền (nếu có)
     */
    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal fee = BigDecimal.ZERO;
    
    /**
     * Số tiền thực nhận sau khi trừ phí
     */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal netAmount;
    
    /**
     * Trạng thái yêu cầu
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private WithdrawalStatus status = WithdrawalStatus.PENDING;
    
    /**
     * Thông tin tài khoản ngân hàng nhận tiền
     */
    @Column(nullable = false, length = 50)
    private String bankName;
    
    @Column(nullable = false, length = 50)
    private String bankAccountNumber;
    
    @Column(nullable = false, length = 100)
    private String bankAccountName;
    
    /**
     * Chi nhánh ngân hàng (nếu cần)
     */
    @Column(length = 100)
    private String bankBranch;
    
    /**
     * Lý do rút tiền (user cung cấp)
     */
    @Column(length = 500)
    private String reason;
    
    /**
     * Ghi chú từ user
     */
    @Column(length = 1000)
    private String userNotes;
    
    /**
     * Mã PIN giao dịch đã xác thực
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean pinVerified = false;
    
    /**
     * Đã xác thực 2FA chưa
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean twoFAVerified = false;
    
    /**
     * Admin duyệt yêu cầu
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;
    
    /**
     * Thời gian admin duyệt
     */
    private LocalDateTime approvedAt;
    
    /**
     * Ghi chú từ admin khi duyệt
     */
    @Column(length = 1000)
    private String adminNotes;
    
    /**
     * Lý do từ chối (nếu bị reject)
     */
    @Column(length = 1000)
    private String rejectionReason;
    
    /**
     * Mã giao dịch ngân hàng (sau khi chuyển tiền thành công)
     */
    @Column(length = 100)
    private String bankTransactionId;
    
    /**
     * Thời gian hoàn tất chuyển tiền
     */
    private LocalDateTime completedAt;
    
    /**
     * Link tham chiếu đến WalletTransaction
     */
    @OneToOne
    @JoinColumn(name = "wallet_transaction_id")
    private WalletTransaction walletTransaction;
    
    /**
     * Ưu tiên xử lý (1 = cao nhất, 5 = thấp nhất)
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 3;
    
    /**
     * Địa chỉ IP khi tạo yêu cầu
     */
    @Column(length = 50)
    private String ipAddress;
    
    /**
     * User Agent
     */
    @Column(length = 255)
    private String userAgent;
    
    /**
     * Số lần thử lại (nếu chuyển tiền thất bại)
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer retryCount = 0;
    
    /**
     * Thời gian thử lại lần cuối
     */
    private LocalDateTime lastRetryAt;
    
    /**
     * Lỗi khi xử lý (nếu có)
     */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    /**
     * Thời hạn xử lý (nếu quá thời hạn sẽ tự động hủy)
     */
    private LocalDateTime expiresAt;
    
    // ==================== ENUM ====================
    
    public enum WithdrawalStatus {
        PENDING("Chờ xử lý", "Yêu cầu đang chờ admin duyệt"),
        APPROVED("Đã duyệt", "Admin đã duyệt, đang chờ chuyển tiền"),
        PROCESSING("Đang xử lý", "Đang chuyển tiền qua ngân hàng"),
        COMPLETED("Hoàn thành", "Đã chuyển tiền thành công"),
        REJECTED("Từ chối", "Yêu cầu bị từ chối bởi admin"),
        CANCELLED("Đã hủy", "User đã hủy yêu cầu"),
        FAILED("Thất bại", "Chuyển tiền thất bại"),
        EXPIRED("Hết hạn", "Yêu cầu quá thời hạn xử lý");
        
        private final String displayName;
        private final String description;
        
        WithdrawalStatus(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDescription() {
            return description;
        }
        
        public boolean isFinalStatus() {
            return this == COMPLETED || this == REJECTED || 
                   this == CANCELLED || this == FAILED || this == EXPIRED;
        }
        
        public boolean canCancel() {
            return this == PENDING || this == APPROVED;
        }
    }
    
    // ==================== BUSINESS METHODS ====================
    
    /**
     * Duyệt yêu cầu rút tiền
     */
    public void approve(User admin, String notes) {
        if (this.status != WithdrawalStatus.PENDING) {
            throw new IllegalStateException("Chỉ có thể duyệt yêu cầu đang chờ xử lý");
        }
        this.status = WithdrawalStatus.APPROVED;
        this.approvedBy = admin;
        this.approvedAt = LocalDateTime.now();
        this.adminNotes = notes;
    }
    
    /**
     * Từ chối yêu cầu rút tiền
     */
    public void reject(User admin, String reason) {
        if (this.status != WithdrawalStatus.PENDING) {
            throw new IllegalStateException("Chỉ có thể từ chối yêu cầu đang chờ xử lý");
        }
        this.status = WithdrawalStatus.REJECTED;
        this.approvedBy = admin;
        this.approvedAt = LocalDateTime.now();
        this.rejectionReason = reason;
    }
    
    /**
     * Hủy yêu cầu rút tiền
     */
    public void cancel() {
        if (!this.status.canCancel()) {
            throw new IllegalStateException("Không thể hủy yêu cầu ở trạng thái: " + this.status);
        }
        this.status = WithdrawalStatus.CANCELLED;
    }
    
    /**
     * Đánh dấu đang xử lý
     */
    public void markProcessing() {
        if (this.status != WithdrawalStatus.APPROVED) {
            throw new IllegalStateException("Chỉ có thể xử lý yêu cầu đã được duyệt");
        }
        this.status = WithdrawalStatus.PROCESSING;
    }
    
    /**
     * Đánh dấu hoàn thành
     */
    public void complete(String bankTransactionId) {
        if (this.status != WithdrawalStatus.PROCESSING && 
            this.status != WithdrawalStatus.APPROVED) {
            throw new IllegalStateException("Chỉ có thể hoàn thành yêu cầu đã được duyệt hoặc đang xử lý");
        }
        this.status = WithdrawalStatus.COMPLETED;
        this.bankTransactionId = bankTransactionId;
        this.completedAt = LocalDateTime.now();
    }
    
    /**
     * Đánh dấu thất bại
     */
    public void fail(String errorMessage) {
        this.status = WithdrawalStatus.FAILED;
        this.errorMessage = errorMessage;
        this.retryCount++;
        this.lastRetryAt = LocalDateTime.now();
    }
    
    /**
     * Tính toán số tiền thực nhận
     */
    public void calculateNetAmount() {
        this.netAmount = this.amount.subtract(this.fee);
    }
    
    /**
     * Kiểm tra có hết hạn không
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
    
    /**
     * Tạo mã yêu cầu tự động
     */
    public static String generateRequestCode() {
        return String.format("WD-%d-%04d", 
            System.currentTimeMillis() / 1000,
            (int)(Math.random() * 10000));
    }
}
