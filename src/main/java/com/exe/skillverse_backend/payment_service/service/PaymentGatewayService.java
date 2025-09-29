package com.exe.skillverse_backend.payment_service.service;

import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Interface for payment gateway integrations
 */
public interface PaymentGatewayService {

    /**
     * Create payment with gateway
     */
    Map<String, Object> createPayment(PaymentTransaction transaction, String successUrl, String cancelUrl);

    /**
     * Verify payment status with gateway
     */
    PaymentTransaction.PaymentStatus verifyPayment(String gatewayReference);

    /**
     * Process refund with gateway
     */
    boolean processRefund(String gatewayReference, BigDecimal amount, String reason);

    /**
     * Get supported payment method
     */
    PaymentTransaction.PaymentMethod getSupportedPaymentMethod();

    /**
     * Validate callback signature
     */
    boolean validateCallback(String signature, Map<String, Object> data);
}