package com.exe.skillverse_backend.wallet_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Wallet Entity - Quản lý ví điện tử của người dùng
 * Mỗi user có 1 ví chứa 2 loại tài sản:
 * 1. cashBalance: Tiền thật (VNĐ) - có thể rút về tài khoản ngân hàng
 * 2. coinBalance: Tiền ảo (SkillCoin) - dùng trong hệ thống, không thể rút
 */
@Entity
@Table(name = "wallets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long walletId;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;
    
    /**
     * Số dư tiền thật (VNĐ)
     * - Được nạp từ PayOS
     * - Có thể rút về tài khoản ngân hàng (cần duyệt)
     * - Có thể dùng để mua Coin
     */
    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal cashBalance = BigDecimal.ZERO;
    
    /**
     * Số dư tiền ảo (SkillCoin)
     * - Mua từ cashBalance hoặc PayOS
     * - Dùng để mua khóa học, premium, gửi tip mentor
     * - KHÔNG thể rút về tiền thật
     */
    @Column(nullable = false)
    @Builder.Default
    private Long coinBalance = 0L;
    
    /**
     * Tổng số tiền đã nạp vào hệ thống (VNĐ)
     * Dùng cho thống kê và xếp hạng người dùng
     */
    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalDeposited = BigDecimal.ZERO;
    
    /**
     * Tổng số tiền đã rút ra (VNĐ)
     */
    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalWithdrawn = BigDecimal.ZERO;
    
    /**
     * Tổng số Coin đã kiếm được (từ học tập, hoàn thành bài tập)
     */
    @Column(nullable = false)
    @Builder.Default
    private Long totalCoinsEarned = 0L;
    
    /**
     * Tổng số Coin đã chi tiêu
     */
    @Column(nullable = false)
    @Builder.Default
    private Long totalCoinsSpent = 0L;
    
    /**
     * Trạng thái ví
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private WalletStatus status = WalletStatus.ACTIVE;
    
    /**
     * Số dư tạm giữ (khi có yêu cầu rút tiền đang chờ duyệt)
     */
    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal frozenCashBalance = BigDecimal.ZERO;
    
    /**
     * Thông tin tài khoản ngân hàng để rút tiền
     */
    @Column(length = 50)
    private String bankName;
    
    @Column(length = 50)
    private String bankAccountNumber;
    
    @Column(length = 100)
    private String bankAccountName;
    
    /**
     * Mã xác thực PIN cho giao dịch quan trọng (rút tiền)
     * Được mã hóa bằng BCrypt
     */
    @Column(length = 255)
    private String transactionPin;
    
    /**
     * Có yêu cầu xác thực 2FA cho giao dịch không
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean require2FA = false;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    /**
     * Lần cuối cùng có giao dịch
     */
    private LocalDateTime lastTransactionAt;
    
    // ==================== ENUM ====================
    
    public enum WalletStatus {
        ACTIVE,      // Hoạt động bình thường
        SUSPENDED,   // Tạm khóa (vi phạm chính sách)
        LOCKED,      // Khóa (quá nhiều giao dịch bất thường)
        CLOSED       // Đóng vĩnh viễn
    }
    
    // ==================== BUSINESS METHODS ====================
    
    /**
     * Thêm tiền vào ví Cash (từ nạp tiền PayOS)
     */
    public void depositCash(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền nạp phải lớn hơn 0");
        }
        this.cashBalance = this.cashBalance.add(amount);
        this.totalDeposited = this.totalDeposited.add(amount);
        this.lastTransactionAt = LocalDateTime.now();
    }
    
    /**
     * Trừ tiền từ ví Cash (mua Coin, rút tiền)
     */
    public void deductCash(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền trừ phải lớn hơn 0");
        }
        
        BigDecimal availableBalance = this.cashBalance.subtract(this.frozenCashBalance);
        if (availableBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Số dư không đủ. Có sẵn: " + availableBalance + " VNĐ");
        }
        
        this.cashBalance = this.cashBalance.subtract(amount);
        this.lastTransactionAt = LocalDateTime.now();
    }
    
    /**
     * Thêm Coin vào ví (từ mua Coin hoặc kiếm được)
     */
    public void addCoins(Long coins) {
        if (coins <= 0) {
            throw new IllegalArgumentException("Số Coin phải lớn hơn 0");
        }
        this.coinBalance += coins;
        this.lastTransactionAt = LocalDateTime.now();
    }
    
    /**
     * Trừ Coin từ ví (chi tiêu)
     */
    public void deductCoins(Long coins) {
        if (coins <= 0) {
            throw new IllegalArgumentException("Số Coin phải lớn hơn 0");
        }
        if (this.coinBalance < coins) {
            throw new IllegalStateException("Số dư Coin không đủ. Hiện có: " + this.coinBalance + " Coins");
        }
        this.coinBalance -= coins;
        this.totalCoinsSpent += coins;
        this.lastTransactionAt = LocalDateTime.now();
    }
    
    /**
     * Ghi nhận Coin kiếm được (từ học tập, hoàn thành bài tập)
     */
    public void earnCoins(Long coins) {
        if (coins <= 0) {
            throw new IllegalArgumentException("Số Coin phải lớn hơn 0");
        }
        this.coinBalance += coins;
        this.totalCoinsEarned += coins;
        this.lastTransactionAt = LocalDateTime.now();
    }
    
    /**
     * Đóng băng số tiền (khi tạo yêu cầu rút tiền)
     */
    public void freezeCash(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền đóng băng phải lớn hơn 0");
        }
        
        BigDecimal availableBalance = this.cashBalance.subtract(this.frozenCashBalance);
        if (availableBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Số dư khả dụng không đủ để đóng băng");
        }
        
        this.frozenCashBalance = this.frozenCashBalance.add(amount);
    }
    
    /**
     * Giải phóng tiền đóng băng (hủy yêu cầu rút tiền)
     */
    public void unfreezeCash(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền giải phóng phải lớn hơn 0");
        }
        if (this.frozenCashBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Số tiền đóng băng không đủ để giải phóng");
        }
        this.frozenCashBalance = this.frozenCashBalance.subtract(amount);
    }
    
    /**
     * Hoàn tất rút tiền (giảm cả số dư và tiền đóng băng)
     */
    public void completeWithdrawal(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền rút phải lớn hơn 0");
        }
        if (this.frozenCashBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Số tiền đóng băng không đủ");
        }
        if (this.cashBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Số dư ví không đủ");
        }
        
        this.cashBalance = this.cashBalance.subtract(amount);
        this.frozenCashBalance = this.frozenCashBalance.subtract(amount);
        this.totalWithdrawn = this.totalWithdrawn.add(amount);
        this.lastTransactionAt = LocalDateTime.now();
    }
    
    /**
     * Kiểm tra có đủ số dư Cash không (không tính tiền đóng băng)
     */
    public boolean hasAvailableCash(BigDecimal amount) {
        BigDecimal available = this.cashBalance.subtract(this.frozenCashBalance);
        return available.compareTo(amount) >= 0;
    }
    
    /**
     * Lấy số dư Cash khả dụng (đã trừ tiền đóng băng)
     */
    public BigDecimal getAvailableCashBalance() {
        return this.cashBalance.subtract(this.frozenCashBalance);
    }
}
