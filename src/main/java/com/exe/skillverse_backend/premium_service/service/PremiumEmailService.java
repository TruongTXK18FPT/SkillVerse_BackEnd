package com.exe.skillverse_backend.premium_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.premium_service.entity.UserSubscription;
import java.math.BigDecimal;

public interface PremiumEmailService {
    void sendPremiumPurchaseSuccessEmail(User user, UserSubscription subscription, BigDecimal paidAmount, String paymentMethod);
}