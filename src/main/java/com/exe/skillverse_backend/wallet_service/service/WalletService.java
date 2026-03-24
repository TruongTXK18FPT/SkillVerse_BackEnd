package com.exe.skillverse_backend.wallet_service.service;

import com.exe.skillverse_backend.payment_service.dto.response.CreatePaymentResponse;
import com.exe.skillverse_backend.wallet_service.dto.response.WalletResponse;
import com.exe.skillverse_backend.wallet_service.dto.response.WalletTransactionResponse;
import com.exe.skillverse_backend.wallet_service.entity.Wallet;
import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WalletService {
    Wallet createWallet(Long userId);

    WalletResponse getWalletByUserId(Long userId);

    Wallet getOrCreateWallet(Long userId);

    WalletTransaction depositCash(Long userId, BigDecimal amount, String paymentReferenceId, String description);

    WalletTransaction addCoins(Long userId, Long coinAmount, WalletTransaction.TransactionType transactionType,
            String description, String referenceType, String referenceId);

    WalletTransaction deductCoins(Long userId, Long coinAmount, WalletTransaction.TransactionType transactionType,
            String description, String referenceType, String referenceId);

    void setTransactionPin(Long userId, String pin);

    boolean verifyTransactionPin(Long userId, String pin);

    void updateBankAccount(Long userId, String bankName, String bankAccountNumber, String bankAccountName);

    void toggle2FA(Long userId, boolean enabled);

    Page<WalletTransactionResponse> getTransactionHistory(Long userId, Pageable pageable);

    WalletTransactionResponse getTransactionDetail(Long userId, Long transactionId);

    Map<String, Object> getWalletStatistics(Long userId);

    CreatePaymentResponse createDepositPayment(Long userId, BigDecimal amount, String paymentMethod, String returnUrl,
            String cancelUrl);

    Map<String, Object> getGlobalStatistics();

    Map<String, Object> getDailyStatistics(String startDate, String endDate);

    WalletTransaction deductCash(Long userId, BigDecimal cashAmount, String description, String referenceType,
            String referenceId);

    WalletTransaction freezeCashForBooking(Long userId, BigDecimal amount, Long bookingId);

    WalletTransaction freezeCashForBooking(Long userId, BigDecimal amount, Long bookingId, String description);

    WalletTransaction chargeFrozenForBooking(Long userId, BigDecimal amount, Long bookingId);

    WalletTransaction chargeFrozenForBooking(Long userId, BigDecimal amount, Long bookingId, String description);

    WalletTransaction unfreezeForBooking(Long userId, BigDecimal amount, Long bookingId);

    WalletTransaction unfreezeForBooking(Long userId, BigDecimal amount, Long bookingId, String description);

    WalletTransaction processRefund(Long userId, BigDecimal cashAmount, String description, String referenceId);

    WalletTransaction payMentorForBooking(Long mentorId, BigDecimal amount, Long bookingId);

    WalletTransaction payMentorForCourse(Long mentorId, BigDecimal amount, Long courseId);

    WalletTransaction payMentorForJobPayout(Long mentorId, BigDecimal amount, Long jobId);

    WalletTransaction payRecruiterForSeminar(Long recruiterId, BigDecimal amount, Long seminarId);

    Map<String, Object> getSystemWalletStats();

    Page<WalletTransactionResponse> getAllTransactionsAdmin(String type, Pageable pageable);

    WalletTransaction giftUser(Long userId, BigDecimal cashAmount, Long coinAmount, String reason);

    boolean hasAvailableCash(Long userId, BigDecimal amount);
}