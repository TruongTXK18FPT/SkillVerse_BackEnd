package com.exe.skillverse_backend.payment_service.service;

import com.exe.skillverse_backend.payment_service.dto.request.CreatePaymentRequest;
import com.exe.skillverse_backend.payment_service.dto.response.CreatePaymentResponse;
import com.exe.skillverse_backend.payment_service.dto.response.PaymentTransactionResponse;
import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

    // ==================== ADMIN METHODS ====================

    /**
     * Admin: Get all payment transactions with filtering
     */
    Page<PaymentTransactionResponse> getAllTransactionsAdmin(
            String status,
            Long userId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );

    /**
     * Admin: Get payment transaction detail by ID
     */
    PaymentTransactionResponse getTransactionByIdAdmin(Long id);

    /**
     * Admin: Get payment statistics
     */
    Map<String, Object> getPaymentStatistics(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Admin: Get revenue breakdown by time period (daily, weekly, monthly, yearly)
     * @param period - "daily", "weekly", "monthly", "yearly"
     * @param days - number of days to look back for daily/weekly, months for monthly
     */
    Map<String, Object> getRevenueBreakdown(String period, int days);
}