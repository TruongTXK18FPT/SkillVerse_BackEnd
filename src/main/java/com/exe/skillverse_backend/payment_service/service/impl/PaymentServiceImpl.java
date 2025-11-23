package com.exe.skillverse_backend.payment_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.payment_service.dto.request.CreatePaymentRequest;
import com.exe.skillverse_backend.payment_service.dto.response.CreatePaymentResponse;
import com.exe.skillverse_backend.payment_service.dto.response.PaymentTransactionResponse;
import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;
import com.exe.skillverse_backend.payment_service.repository.PaymentTransactionRepository;
import com.exe.skillverse_backend.payment_service.service.PaymentService;
import com.exe.skillverse_backend.premium_service.service.PremiumService;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import com.exe.skillverse_backend.user_service.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final UserRepository userRepository;
    private final PayOSGatewayService payOSGatewayService;
    private final PremiumService premiumService;
    private final WalletService walletService;
    private final UserProfileService userProfileService;

    @Override
    @Transactional
    public CreatePaymentResponse createPayment(Long userId, CreatePaymentRequest request) {
        log.info("Creating payment for user {} with amount {} {}", userId, request.getAmount(), request.getCurrency());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        PaymentTransaction transaction = PaymentTransaction.builder()
                .user(user)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .type(request.getType())
                .paymentMethod(request.getPaymentMethod())
                .description(request.getDescription())
                .metadata(request.getMetadata())
                .status(PaymentTransaction.PaymentStatus.PENDING)
                .build();

        transaction = paymentTransactionRepository.save(transaction);
        log.info("Created payment transaction with ID: {} and reference: {}", transaction.getId(),
                transaction.getInternalReference());

        // Create PayOS payment if method is PAYOS
        String checkoutUrl = null;
        String referenceId = null;

        if (request.getPaymentMethod() == PaymentTransaction.PaymentMethod.PAYOS) {
            try {
                String successUrlWithRef = appendQueryParam(request.getSuccessUrl(), "ref",
                        transaction.getInternalReference());
                String cancelUrlWithRef = appendQueryParam(
                        appendQueryParam(request.getCancelUrl(), "ref", transaction.getInternalReference()),
                        "cancel", "1");

                Map<String, Object> payOSResult = payOSGatewayService.createPayment(
                        transaction,
                        successUrlWithRef,
                        cancelUrlWithRef);

                checkoutUrl = (String) payOSResult.get("checkoutUrl");
                referenceId = (String) payOSResult.get("referenceId");

                // Update transaction with PayOS reference
                transaction.setReferenceId(referenceId);
                paymentTransactionRepository.save(transaction);

                log.info("PayOS payment created with reference: {}", referenceId);
            } catch (Exception e) {
                log.error("Failed to create PayOS payment: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to create PayOS payment", e);
            }
        }

        return CreatePaymentResponse.builder()
                .transactionReference(transaction.getInternalReference())
                .checkoutUrl(checkoutUrl)
                .gatewayReferenceId(referenceId)
                .message("Payment created successfully")
                .build();
    }

    private String appendQueryParam(String url, String key, String value) {
        if (url == null || url.isEmpty())
            return url;
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + key + "=" + java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public Optional<PaymentTransactionResponse> getPaymentByReference(String internalReference) {
        Optional<PaymentTransaction> txOpt = paymentTransactionRepository.findByInternalReference(internalReference);
        if (txOpt.isEmpty()) {
            return Optional.empty();
        }

        PaymentTransaction tx = txOpt.get();

        // Fallback verification with gateway while polling from FE
        if (tx.getPaymentMethod() == PaymentTransaction.PaymentMethod.PAYOS
                && tx.getStatus() == PaymentTransaction.PaymentStatus.PENDING
                && tx.getReferenceId() != null) {
            try {
                PaymentTransaction.PaymentStatus gatewayStatus = payOSGatewayService.verifyPayment(tx.getReferenceId());
                if (gatewayStatus != PaymentTransaction.PaymentStatus.PENDING
                        && gatewayStatus != tx.getStatus()) {
                    tx.setStatus(gatewayStatus);
                    paymentTransactionRepository.save(tx);

                    // Auto-activate subscription on success
                    if (gatewayStatus == PaymentTransaction.PaymentStatus.COMPLETED
                            && tx.getType() == PaymentTransaction.PaymentType.PREMIUM_SUBSCRIPTION) {
                        try {
                            String subscriptionIdStr = extractSubscriptionIdFromMetadata(
                                    tx.getMetadata() != null ? tx.getMetadata() : "");
                            if (subscriptionIdStr != null) {
                                Long subscriptionId = Long.parseLong(subscriptionIdStr);
                                premiumService.activateSubscription(subscriptionId, tx.getInternalReference());
                                log.info("Auto-activated subscription {} for payment {} via verify",
                                        subscriptionId, tx.getInternalReference());
                            }
                        } catch (Exception e) {
                            log.warn("Failed to auto-activate after verify: {}", e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Gateway verify failed for {}: {}", tx.getReferenceId(), e.getMessage());
            }
        }

        return Optional.of(convertToResponse(tx));
    }

    @Override
    public Optional<PaymentTransactionResponse> getPaymentById(Long paymentId) {
        return paymentTransactionRepository.findById(paymentId)
                .map(this::convertToResponse);
    }

    @Override
    public List<PaymentTransactionResponse> getUserPaymentHistory(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return paymentTransactionRepository
                .findByUserOrderByCreatedAtDesc(user, org.springframework.data.domain.Pageable.unpaged())
                .getContent()
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PaymentTransaction processPaymentCallback(String gatewayReference, String status, String metadata) {
        log.info("Processing payment callback for reference: {} with status: {}", gatewayReference, status);

        var transactionOpt = paymentTransactionRepository.findByReferenceId(gatewayReference);
        if (transactionOpt.isEmpty()) {
            // Check if this is a test webhook from PayOS (orderCode like "123", "456", etc.)
            if (gatewayReference.matches("^\\d{1,3}$")) {
                log.warn("⚠️ Ignoring test webhook from PayOS - orderCode: {}", gatewayReference);
                throw new RuntimeException("Test webhook ignored: " + gatewayReference);
            }
            
            log.error("❌ Payment transaction not found with referenceId: '{}'. Gateway status: {}", gatewayReference, status);
            throw new RuntimeException("Payment transaction not found: " + gatewayReference);
        }

        PaymentTransaction transaction = transactionOpt.get();
        log.info("✅ Found payment transaction - ID: {}, User: {}, Current Status: {}, Type: {}",
            transaction.getId(), transaction.getUser().getId(), transaction.getStatus(), transaction.getType());

        PaymentTransaction.PaymentStatus newStatus = switch (status.toUpperCase()) {
            case "SUCCESS", "COMPLETED", "PAID" -> PaymentTransaction.PaymentStatus.COMPLETED;
            case "FAILED", "ERROR" -> PaymentTransaction.PaymentStatus.FAILED;
            case "CANCELLED" -> PaymentTransaction.PaymentStatus.CANCELLED;
            default -> PaymentTransaction.PaymentStatus.PENDING;
        };

        // Idempotency: Check if already processed with same or better status
        // to prevent duplicate wallet deposits and transaction creation
        if (transaction.getStatus() == PaymentTransaction.PaymentStatus.COMPLETED &&
            newStatus == PaymentTransaction.PaymentStatus.COMPLETED) {
            log.warn("⚠️ Callback already processed for payment: {} (status already COMPLETED). Ignoring duplicate.", gatewayReference);
            return transaction;
        }

        transaction.setStatus(newStatus);
        // Preserve original metadata that contains subscriptionId; don't overwrite with
        // webhook payload
        if (metadata != null && metadata.contains("subscriptionId")) {
            transaction.setMetadata(metadata);
        }

        PaymentTransaction savedTransaction = paymentTransactionRepository.save(transaction);

        // Auto-activate subscription if payment is completed and it's a premium
        // subscription
        if (newStatus == PaymentTransaction.PaymentStatus.COMPLETED &&
                transaction.getType() == PaymentTransaction.PaymentType.PREMIUM_SUBSCRIPTION) {

            try {
                // Extract subscriptionId from metadata
                if (transaction.getMetadata() != null && !transaction.getMetadata().isEmpty()) {
                    // Assuming metadata contains JSON with subscriptionId
                    // You might want to use a proper JSON parser here
                    String subscriptionIdStr = extractSubscriptionIdFromMetadata(transaction.getMetadata());
                    if (subscriptionIdStr != null) {
                        Long subscriptionId = Long.parseLong(subscriptionIdStr);
                        premiumService.activateSubscription(subscriptionId, transaction.getInternalReference());
                        log.info("Auto-activated subscription {} for payment {}", subscriptionId,
                                transaction.getInternalReference());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to auto-activate subscription for payment {}: {}", transaction.getInternalReference(),
                        e.getMessage(), e);
                // Don't fail the callback processing if subscription activation fails
            }
        }
        
        // Handle wallet deposit if payment is completed and it's a wallet deposit
        if (newStatus == PaymentTransaction.PaymentStatus.COMPLETED &&
                transaction.getType() == PaymentTransaction.PaymentType.WALLET_TOPUP) {

            try {
                log.info("💰 Processing wallet deposit - User: {}, Amount: {}, Reference: {}",
                        transaction.getUser().getId(), transaction.getAmount(), transaction.getInternalReference());

                walletService.depositCash(
                        transaction.getUser().getId(),
                        transaction.getAmount(),
                        transaction.getInternalReference(),
                        transaction.getDescription() != null ?
                                transaction.getDescription() :
                                "Nạp tiền qua PayOS"
                );

                log.info("✅ Successfully deposited {} VNĐ to wallet for user {}",
                        transaction.getAmount(), transaction.getUser().getId());
            } catch (Exception e) {
                log.error("❌ Failed to deposit to wallet for payment {}: {}",
                        transaction.getInternalReference(), e.getMessage(), e);
                // Mark transaction as failed if wallet deposit fails
                transaction.setStatus(PaymentTransaction.PaymentStatus.FAILED);
                transaction.setFailureReason("Wallet deposit failed: " + e.getMessage());
                paymentTransactionRepository.save(transaction);
                throw new RuntimeException("Wallet deposit failed", e);
            }
        } else if (newStatus == PaymentTransaction.PaymentStatus.COMPLETED) {
            log.info("⚠️ Payment completed but not WALLET_TOPUP - Type: {}, User: {}",
                transaction.getType(), transaction.getUser().getId());
        }

        return savedTransaction;
    }

    private String extractSubscriptionIdFromMetadata(String metadata) {
        // Robust JSON parsing using Jackson
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(metadata);
            com.fasterxml.jackson.databind.JsonNode idNode = node.get("subscriptionId");
            if (idNode != null && !idNode.isNull()) {
                return idNode.asText();
            }
        } catch (Exception e) {
            log.warn("Failed to parse subscriptionId from metadata JSON: {}", metadata);
        }
        return null;
    }

    @Override
    @Transactional
    public PaymentTransaction updatePaymentStatus(String internalReference, PaymentTransaction.PaymentStatus status,
            String failureReason) {
        PaymentTransaction transaction = paymentTransactionRepository.findByInternalReference(internalReference)
                .orElseThrow(() -> new RuntimeException("Payment transaction not found: " + internalReference));

        transaction.setStatus(status);
        if (failureReason != null) {
            transaction.setFailureReason(failureReason);
        }

        return paymentTransactionRepository.save(transaction);
    }

    @Override
    @Transactional
    public void cancelPayment(String internalReference, String reason) {
        PaymentTransaction transaction = paymentTransactionRepository.findByInternalReference(internalReference)
                .orElseThrow(() -> new RuntimeException("Payment transaction not found: " + internalReference));

        if (transaction.getStatus() != PaymentTransaction.PaymentStatus.PENDING) {
            throw new RuntimeException("Cannot cancel payment that is not pending");
        }

        transaction.setStatus(PaymentTransaction.PaymentStatus.CANCELLED);
        transaction.setFailureReason(reason);
        paymentTransactionRepository.save(transaction);
    }

    @Override
    @Transactional
    public PaymentTransaction processRefund(Long paymentId, String reason) {
        PaymentTransaction transaction = paymentTransactionRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment transaction not found: " + paymentId));

        if (transaction.getStatus() != PaymentTransaction.PaymentStatus.COMPLETED) {
            throw new RuntimeException("Cannot refund payment that is not completed");
        }

        transaction.setStatus(PaymentTransaction.PaymentStatus.REFUNDED);
        transaction.setFailureReason(reason);

        return paymentTransactionRepository.save(transaction);
    }

    @Override
    @Transactional
    public boolean verifyPaymentWithGateway(String internalReference) {
        log.info("🔍 Verifying payment with gateway: {}", internalReference);

        Optional<PaymentTransaction> transactionOpt = paymentTransactionRepository
                .findByInternalReference(internalReference);

        if (transactionOpt.isEmpty()) {
            log.warn("❌ Payment transaction not found: {}", internalReference);
            return false;
        }

        PaymentTransaction transaction = transactionOpt.get();

        // If already completed, no need to verify again
        if (transaction.getStatus() == PaymentTransaction.PaymentStatus.COMPLETED) {
            log.info("✅ Payment already completed: {}", internalReference);
            return true;
        }

        try {
            // Verify with PayOS gateway using referenceId (orderCode)
            if (transaction.getReferenceId() != null) {
                log.info("🔄 Verifying with PayOS - orderCode: {}", transaction.getReferenceId());
                PaymentTransaction.PaymentStatus gatewayStatus = payOSGatewayService.verifyPayment(transaction.getReferenceId());

                log.info("📊 PayOS verification result - orderCode: {}, status: {}",
                    transaction.getReferenceId(), gatewayStatus);

                // If payment is completed on gateway, process it (only if not already processed)
                if (gatewayStatus == PaymentTransaction.PaymentStatus.COMPLETED) {
                    log.info("💰 Payment confirmed by PayOS, processing callback...");
                    // Call processPaymentCallback only if status is still PENDING (idempotency check)
                    // This prevents duplicate processing from webhook + verification race condition
                    if (transaction.getStatus() == PaymentTransaction.PaymentStatus.PENDING) {
                        processPaymentCallback(transaction.getReferenceId(), "PAID", null);
                    } else {
                        log.info("⚠️ Payment already processed (status: {}), skipping callback", transaction.getStatus());
                    }
                    return true;
                } else if (gatewayStatus == PaymentTransaction.PaymentStatus.CANCELLED ||
                           gatewayStatus == PaymentTransaction.PaymentStatus.FAILED) {
                    // Update status if cancelled or failed
                    transaction.setStatus(gatewayStatus);
                    paymentTransactionRepository.save(transaction);
                    log.info("⚠️ Payment status updated to: {}", gatewayStatus);
                    return false;
                }
            }

            return false;

        } catch (Exception e) {
            log.error("❌ Error verifying payment with gateway: {}", e.getMessage(), e);
            return false;
        }
    }

    private PaymentTransactionResponse convertToResponse(PaymentTransaction transaction) {
        User user = transaction.getUser();
        String fullName = (user.getFirstName() != null ? user.getFirstName() : "") + 
                         " " + 
                         (user.getLastName() != null ? user.getLastName() : "");
        
        return PaymentTransactionResponse.builder()
                .id(transaction.getId())
                .userId(user.getId())
                .userName(fullName.trim())
                .userEmail(user.getEmail())
                .userAvatarUrl(getUserAvatarUrl(user))
                .internalReference(transaction.getInternalReference())
                .referenceId(transaction.getReferenceId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .type(transaction.getType())
                .paymentMethod(transaction.getPaymentMethod())
                .status(transaction.getStatus())
                .description(transaction.getDescription())
                .failureReason(transaction.getFailureReason())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }

    /**
     * Get user's avatar URL from their profile
     */
    private String getUserAvatarUrl(User user) {
        try {
            if (user.getAvatarUrl() != null) {
                return user.getAvatarUrl();
            }
            
            // Try to get from UserProfile if exists
            if (userProfileService.hasProfile(user.getId())) {
                var profile = userProfileService.getProfile(user.getId());
                if (profile.getAvatarMediaUrl() != null) {
                    return profile.getAvatarMediaUrl();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get avatar URL for user {}: {}", user.getId(), e.getMessage());
        }
        return null;
    }
    
    // ==================== ADMIN METHODS ====================
    
    @Override
    @Transactional(readOnly = true)
    public Page<PaymentTransactionResponse> getAllTransactionsAdmin(
            String status,
            Long userId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    ) {
        log.info("Admin fetching all payment transactions - status: {}, userId: {}", status, userId);
        
        Page<PaymentTransaction> transactions;
        
        // Build query based on filters
        if (status != null && userId != null) {
            PaymentTransaction.PaymentStatus paymentStatus = PaymentTransaction.PaymentStatus.valueOf(status);
            transactions = paymentTransactionRepository.findByStatusAndUserId(paymentStatus, userId, pageable);
        } else if (status != null) {
            PaymentTransaction.PaymentStatus paymentStatus = PaymentTransaction.PaymentStatus.valueOf(status);
            transactions = paymentTransactionRepository.findByStatus(paymentStatus, pageable);
        } else if (userId != null) {
            transactions = paymentTransactionRepository.findByUserId(userId, pageable);
        } else {
            transactions = paymentTransactionRepository.findAll(pageable);
        }
        
        return transactions.map(this::convertToResponse);
    }
    
    @Override
    @Transactional(readOnly = true)
    public PaymentTransactionResponse getTransactionByIdAdmin(Long id) {
        log.info("Admin fetching payment transaction detail for id: {}", id);
        
        PaymentTransaction transaction = paymentTransactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment transaction not found with id: " + id));
        
        return convertToResponse(transaction);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getPaymentStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Admin fetching payment statistics");
        
        // If no date range provided, use last 30 days
        if (startDate == null) {
            startDate = LocalDateTime.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }
        
        List<PaymentTransaction> transactions = paymentTransactionRepository
                .findByCreatedAtBetween(startDate, endDate);
        
        // Calculate statistics
        long totalTransactions = transactions.size();
        long completedCount = transactions.stream()
                .filter(t -> t.getStatus() == PaymentTransaction.PaymentStatus.COMPLETED)
                .count();
        long pendingCount = transactions.stream()
                .filter(t -> t.getStatus() == PaymentTransaction.PaymentStatus.PENDING)
                .count();
        long failedCount = transactions.stream()
                .filter(t -> t.getStatus() == PaymentTransaction.PaymentStatus.FAILED)
                .count();
        
        double totalRevenueValue = transactions.stream()
                .filter(t -> t.getStatus() == PaymentTransaction.PaymentStatus.COMPLETED)
                .map(PaymentTransaction::getAmount)
                .filter(amount -> amount != null)
                .mapToDouble(amount -> {
                    try {
                        // amount is String, parse it
                        return Double.parseDouble(String.valueOf(amount));
                    } catch (Exception e) {
                        return 0.0;
                    }
                })
                .sum();
        String totalRevenue = String.valueOf(totalRevenueValue);
        
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("totalTransactions", totalTransactions);
        stats.put("completedCount", completedCount);
        stats.put("pendingCount", pendingCount);
        stats.put("failedCount", failedCount);
        stats.put("totalRevenue", totalRevenue);
        stats.put("startDate", startDate);
        stats.put("endDate", endDate);
        
        return stats;
    }
}
