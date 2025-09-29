package com.exe.skillverse_backend.payment_service.service;

import com.exe.skillverse_backend.payment_service.dto.request.CreatePaymentRequest;
import com.exe.skillverse_backend.payment_service.dto.response.CreatePaymentResponse;
import com.exe.skillverse_backend.payment_service.dto.response.PaymentTransactionResponse;
import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for payment processing
 */
public interface PaymentService {

    /**
     * Create a new payment transaction
     */
    CreatePaymentResponse createPayment(Long userId, CreatePaymentRequest request);

    /**
     * Get payment transaction by internal reference
     */
    Optional<PaymentTransactionResponse> getPaymentByReference(String internalReference);

    /**
     * Get payment transaction by ID
     */
    Optional<PaymentTransactionResponse> getPaymentById(Long paymentId);

    /**
     * Get user's payment history
     */
    List<PaymentTransactionResponse> getUserPaymentHistory(Long userId);

    /**
     * Process payment callback from gateway
     */
    PaymentTransaction processPaymentCallback(String gatewayReference, String status, String metadata);

    /**
     * Update payment status
     */
    PaymentTransaction updatePaymentStatus(String internalReference, PaymentTransaction.PaymentStatus status,
            String failureReason);

    /**
     * Cancel payment transaction
     */
    void cancelPayment(String internalReference, String reason);

    /**
     * Process refund for completed payment
     */
    PaymentTransaction processRefund(Long paymentId, String reason);

    /**
     * Verify payment with gateway
     */
    boolean verifyPaymentWithGateway(String internalReference);
}