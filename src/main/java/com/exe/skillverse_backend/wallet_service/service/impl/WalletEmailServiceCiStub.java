package com.exe.skillverse_backend.wallet_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.wallet_service.entity.WithdrawalRequest;
import com.exe.skillverse_backend.wallet_service.service.WalletEmailService;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("ci")
@Slf4j
public class WalletEmailServiceCiStub implements WalletEmailService {

    @Override
    public void sendDepositSuccessEmail(User user, BigDecimal amount, String transactionId, BigDecimal currentBalance) {
        log.debug("[CI] Skipping sendDepositSuccessEmail for {}", user.getEmail());
    }

    @Override
    public void sendCoinPurchaseEmail(User user, Long totalCoins, Long bonusCoins, BigDecimal paidAmount, String paymentMethod) {
        log.debug("[CI] Skipping sendCoinPurchaseEmail for {}", user.getEmail());
    }

    @Override
    public void sendWithdrawalRequestCreatedEmail(User user, WithdrawalRequest request) {
        log.debug("[CI] Skipping sendWithdrawalRequestCreatedEmail for {}", user.getEmail());
    }

    @Override
    public void sendWithdrawalApprovedEmail(User user, WithdrawalRequest request) {
        log.debug("[CI] Skipping sendWithdrawalApprovedEmail for {}", user.getEmail());
    }

    @Override
    public void sendWithdrawalRejectedEmail(User user, WithdrawalRequest request) {
        log.debug("[CI] Skipping sendWithdrawalRejectedEmail for {}", user.getEmail());
    }

    @Override
    public void sendWithdrawalCompletedEmail(User user, WithdrawalRequest request) {
        log.debug("[CI] Skipping sendWithdrawalCompletedEmail for {}", user.getEmail());
    }

    @Override
    public void sendAdminWithdrawalNotification(String adminEmail, User user, WithdrawalRequest request) {
        log.debug("[CI] Skipping sendAdminWithdrawalNotification for {}", adminEmail);
    }

    @Override
    public void sendAdminGiftEmail(User user, BigDecimal cashAmount, Long coinAmount, String reason) {
        log.debug("[CI] Skipping sendAdminGiftEmail for {}", user.getEmail());
    }
}
