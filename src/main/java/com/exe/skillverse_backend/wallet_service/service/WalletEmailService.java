package com.exe.skillverse_backend.wallet_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.wallet_service.entity.WithdrawalRequest;
import java.math.BigDecimal;

public interface WalletEmailService {

    void sendDepositSuccessEmail(User user, BigDecimal amount, String transactionId, BigDecimal currentBalance);

    void sendCoinPurchaseEmail(User user, Long totalCoins, Long bonusCoins, BigDecimal paidAmount, String paymentMethod);

    void sendWithdrawalRequestCreatedEmail(User user, WithdrawalRequest request);

    void sendWithdrawalApprovedEmail(User user, WithdrawalRequest request);

    void sendWithdrawalRejectedEmail(User user, WithdrawalRequest request);

    void sendWithdrawalCompletedEmail(User user, WithdrawalRequest request);

    void sendAdminWithdrawalNotification(String adminEmail, User user, WithdrawalRequest request);

    void sendAdminGiftEmail(User user, BigDecimal cashAmount, Long coinAmount, String reason);
}
