package com.exe.skillverse_backend.premium_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.premium_service.entity.UserSubscription;
import java.math.BigDecimal;

public interface PremiumEmailService {
    /**
     * Send premium purchase success email
     */
    void sendPremiumPurchaseSuccessEmail(
            User user,
            UserSubscription subscription,
            BigDecimal paidAmount,
            String paymentMethod);

    /**
     * Send auto-renewal success email
     */
    void sendAutoRenewalSuccessEmail(
            User user,
            UserSubscription subscription,
            BigDecimal renewalAmount);

    /**
     * Send auto-renewal failed email (insufficient balance)
     */
    void sendAutoRenewalFailedEmail(
            User user,
            UserSubscription subscription,
            BigDecimal renewalAmount);
}