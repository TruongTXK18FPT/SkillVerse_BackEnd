package com.exe.skillverse_backend.premium_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;
import com.exe.skillverse_backend.payment_service.repository.PaymentTransactionRepository;
import com.exe.skillverse_backend.premium_service.dto.request.CreateSubscriptionRequest;
import com.exe.skillverse_backend.premium_service.dto.response.PremiumPlanResponse;
import com.exe.skillverse_backend.premium_service.dto.response.UserSubscriptionResponse;
import com.exe.skillverse_backend.premium_service.entity.PremiumPlan;
import com.exe.skillverse_backend.premium_service.entity.UserSubscription;
import com.exe.skillverse_backend.premium_service.entity.SubscriptionCancellation;
import com.exe.skillverse_backend.premium_service.repository.PremiumPlanRepository;
import com.exe.skillverse_backend.premium_service.repository.UserSubscriptionRepository;
import com.exe.skillverse_backend.premium_service.repository.SubscriptionCancellationRepository;
import com.exe.skillverse_backend.premium_service.service.PremiumService;
import com.exe.skillverse_backend.premium_service.service.PremiumEmailService;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import com.exe.skillverse_backend.user_service.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PremiumServiceImpl implements PremiumService {

        private final PremiumPlanRepository premiumPlanRepository;
        private final UserSubscriptionRepository userSubscriptionRepository;
        private final UserRepository userRepository;
        private final PaymentTransactionRepository paymentTransactionRepository;
        private final WalletService walletService;
        private final SubscriptionCancellationRepository cancellationRepository;
        private final UserProfileService userProfileService;
        private final PremiumEmailService premiumEmailService;
        private final NotificationService notificationService;

        private static final List<String> STUDENT_EMAIL_DOMAINS = List.of(
                        ".edu", ".edu.vn", ".ac.uk", "university.", "student.", ".edu.au");

        @Override
        public List<PremiumPlanResponse> getAvailablePlans() {
                log.info("Fetching all available premium plans");
                return premiumPlanRepository.findByIsActiveTrueOrderByPrice()
                                .stream()
                                .map(this::convertToPremiumPlanResponse)
                                .collect(Collectors.toList());
        }

        @Override
        public Optional<PremiumPlanResponse> getPlanById(Long planId) {
                log.info("Fetching premium plan with ID: {}", planId);
                return premiumPlanRepository.findById(planId)
                                .filter(plan -> plan.getIsActive())
                                .map(this::convertToPremiumPlanResponse);
        }

        @Override
        public Optional<PremiumPlanResponse> getPlanByType(PremiumPlan.PlanType planType) {
                log.info("Fetching premium plan with type: {}", planType);
                return premiumPlanRepository.findByPlanTypeAndIsActiveTrue(planType)
                                .map(this::convertToPremiumPlanResponse);
        }

        @Override
        @Transactional
        public UserSubscriptionResponse createSubscription(Long userId, CreateSubscriptionRequest request) {
                log.info("Creating subscription for user {} with plan {}", userId, request.getPlanId());

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

                PremiumPlan plan = premiumPlanRepository.findById(request.getPlanId())
                                .filter(p -> p.getIsActive())
                                .orElseThrow(() -> new RuntimeException(
                                                "Premium plan not found: " + request.getPlanId()));

                Optional<UserSubscription> existingSubscription = userSubscriptionRepository
                                .findByUserAndIsActiveTrue(user);

                if (existingSubscription.isPresent()) {
                        PremiumPlan existingPlan = existingSubscription.get().getPlan();
                        if (existingPlan.getPlanType() != PremiumPlan.PlanType.FREE_TIER) {
                                throw new RuntimeException("User already has an active subscription");
                        }
                        // Deactivate FREE_TIER to allow upgrading
                        UserSubscription activeSub = existingSubscription.get();
                        activeSub.cancel("Upgrading from Free Tier");
                        userSubscriptionRepository.save(activeSub);
                }

                boolean isStudentEligible = request.getApplyStudentDiscount() &&
                                isValidStudentEmail(user.getEmail());

                LocalDateTime startDate = LocalDateTime.now();
                // Enforce fixed 1-month validity for upgrades
                LocalDateTime endDate = startDate.plusMonths(1);

                // Active FREE_TIER (if any) was cancelled above; proceed to create pending paid
                // subscription

                UserSubscription subscription = UserSubscription.builder()
                                .user(user)
                                .plan(plan)
                                .startDate(startDate)
                                .endDate(endDate)
                                .isActive(false)
                                .status(UserSubscription.SubscriptionStatus.PENDING)
                                .isStudentSubscription(isStudentEligible)
                                .autoRenew(request.getAutoRenew())
                                .build();

                subscription = userSubscriptionRepository.save(subscription);
                return convertToUserSubscriptionResponse(subscription);
        }

        @Override
        public Optional<UserSubscriptionResponse> getCurrentSubscription(Long userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));
                return userSubscriptionRepository.findByUserAndIsActiveTrue(user)
                                .map(this::convertToUserSubscriptionResponse);
        }

        @Override
        public List<UserSubscriptionResponse> getSubscriptionHistory(Long userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));
                return userSubscriptionRepository.findByUserOrderByCreatedAtDesc(user, Pageable.unpaged())
                                .getContent()
                                .stream()
                                .map(this::convertToUserSubscriptionResponse)
                                .collect(Collectors.toList());
        }

        @Override
        @Transactional
        public void cancelSubscription(Long userId, String reason) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));
                UserSubscription subscription = userSubscriptionRepository.findByUserAndIsActiveTrue(user)
                                .orElseThrow(() -> new RuntimeException("No active subscription found"));
                subscription.cancel(reason);
                userSubscriptionRepository.save(subscription);
        }

        @Override
        @Transactional
        public UserSubscription activateSubscription(Long subscriptionId, String paymentTransactionId) {
                UserSubscription subscription = userSubscriptionRepository.findById(subscriptionId)
                                .orElseThrow(() -> new RuntimeException("Subscription not found"));

                PaymentTransaction paymentTransaction = paymentTransactionRepository
                                .findByInternalReference(paymentTransactionId)
                                .orElseThrow(() -> new RuntimeException("Payment transaction not found"));

                if (paymentTransaction.getStatus() != PaymentTransaction.PaymentStatus.COMPLETED) {
                        throw new RuntimeException("Payment transaction is not completed");
                }

                subscription.setIsActive(true);
                subscription.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
                subscription.setPaymentTransaction(paymentTransaction);

                UserSubscription savedSubscription = userSubscriptionRepository.save(subscription);

                // Send premium purchase success email
                premiumEmailService.sendPremiumPurchaseSuccessEmail(
                                subscription.getUser(),
                                savedSubscription,
                                paymentTransaction.getAmount(),
                                paymentTransaction.getPaymentMethod().name());

                return savedSubscription;
        }

        @Override
        public boolean hasActivePremiumSubscription(Long userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));
                // Premium means active non-free subscription
                return userSubscriptionRepository.hasActiveNonFreeSubscription(user, LocalDateTime.now());
        }

        @Override
        public boolean isValidStudentEmail(String email) {
                if (email == null || email.isEmpty()) {
                        return false;
                }
                String lowerEmail = email.toLowerCase();
                return STUDENT_EMAIL_DOMAINS.stream()
                                .anyMatch(lowerEmail::contains);
        }

        @Scheduled(cron = "0 0 9 * * ?") // Run at 9 AM daily
        @Transactional
        public void notifyExpiringSubscriptions() {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime start = now.plusDays(3).withHour(0).withMinute(0).withSecond(0);
                LocalDateTime end = now.plusDays(3).withHour(23).withMinute(59).withSecond(59);

                List<UserSubscription> expiring = userSubscriptionRepository.findSubscriptionsExpiringSoon(start, end);

                for (UserSubscription sub : expiring) {
                        notificationService.createNotification(
                                        sub.getUser().getId(),
                                        "Gói Premium sắp hết hạn",
                                        "Gói Premium của bạn sẽ hết hạn vào ngày " + sub.getEndDate().toLocalDate()
                                                        + ". Hãy gia hạn để không bị gián đoạn.",
                                        NotificationType.PREMIUM_EXPIRATION,
                                        String.valueOf(sub.getId()));
                }
        }

        @Override
        @Scheduled(cron = "0 0 2 * * ?") // Run at 2 AM daily
        @Transactional
        public void processAutoRenewals() {
                log.info("🔄 Starting auto-renewal process...");

                LocalDateTime now = LocalDateTime.now();
                LocalDateTime renewalWindow = now.plusDays(3); // Renew 3 days before expiry

                List<UserSubscription> subscriptionsToRenew = userSubscriptionRepository
                                .findSubscriptionsForAutoRenewal(now, renewalWindow);

                log.info("Found {} subscriptions eligible for auto-renewal", subscriptionsToRenew.size());

                int successCount = 0;
                int failCount = 0;

                for (UserSubscription subscription : subscriptionsToRenew) {
                        try {
                                processAutoRenewal(subscription);
                                successCount++;
                        } catch (Exception e) {
                                log.error("Failed to auto-renew subscription {} for user {}: {}",
                                                subscription.getId(),
                                                subscription.getUser().getId(),
                                                e.getMessage());
                                failCount++;
                        }
                }

                log.info("✅ Auto-renewal process completed. Success: {}, Failed: {}", successCount, failCount);
        }

        private void processAutoRenewal(UserSubscription subscription) {
                log.info("Processing auto-renewal for subscription {} (user: {})",
                                subscription.getId(),
                                subscription.getUser().getId());

                User user = subscription.getUser();
                PremiumPlan currentPlan = subscription.getPlan();

                // Calculate price with student discount if applicable
                BigDecimal price = currentPlan.getPrice();
                if (subscription.getIsStudentSubscription()) {
                        price = price.multiply(BigDecimal.valueOf(0.8)); // 20% student discount
                }

                // Try to deduct from wallet
                try {
                        walletService.deductCash(user.getId(), price,
                                        "Gia hạn tự động gói " + currentPlan.getDisplayName(),
                                        "AUTO_RENEWAL",
                                        subscription.getId().toString());

                        // Calculate new end date
                        LocalDateTime newStartDate = subscription.getEndDate();
                        LocalDateTime newEndDate = calculateEndDate(newStartDate, currentPlan.getDurationMonths());

                        // Update subscription
                        subscription.setStartDate(newStartDate);
                        subscription.setEndDate(newEndDate);
                        subscription.setIsActive(true);
                        subscription.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
                        // Keep autoRenew = true for next cycle

                        userSubscriptionRepository.save(subscription);

                        log.info("✅ Auto-renewed subscription {} until {}",
                                        subscription.getId(),
                                        newEndDate);

                } catch (Exception e) {
                        log.error("❌ Auto-renewal failed for subscription {}: Insufficient balance. Disabling auto-renewal.",
                                        subscription.getId());

                        // Disable auto-renewal if payment fails
                        subscription.setAutoRenew(false);
                        userSubscriptionRepository.save(subscription);

                        // TODO: Send notification to user about failed auto-renewal
                }
        }

        @Override
        @Scheduled(cron = "0 0 * * * ?")
        @Transactional
        public void processExpiredSubscriptions() {
                LocalDateTime now = LocalDateTime.now();
                userSubscriptionRepository.markExpiredSubscriptions(now);

                // Fallback users to Free Tier
                premiumPlanRepository.findByPlanTypeAndIsActiveTrue(PremiumPlan.PlanType.FREE_TIER)
                                .ifPresent(freePlan -> {
                                        List<User> users = userRepository.findAll();
                                        for (User user : users) {
                                                boolean hasActive = userSubscriptionRepository
                                                                .hasActiveSubscription(user, now);
                                                if (!hasActive) {
                                                        UserSubscription freeSub = UserSubscription.builder()
                                                                        .user(user)
                                                                        .plan(freePlan)
                                                                        .startDate(now)
                                                                        .endDate(now.plusYears(100))
                                                                        .isActive(true)
                                                                        .status(UserSubscription.SubscriptionStatus.ACTIVE)
                                                                        .autoRenew(false)
                                                                        .build();
                                                        userSubscriptionRepository.save(freeSub);
                                                }
                                        }
                                });

                log.info("Processed expired subscriptions and reverted to Free Tier at {}", now);
        }

        @Override
        @Transactional
        public void assignFreeTierIfMissing(Long userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));
                LocalDateTime now = LocalDateTime.now();
                boolean hasActive = userSubscriptionRepository.hasActiveSubscription(user, now);
                if (hasActive) {
                        return;
                }
                PremiumPlan freePlan = premiumPlanRepository
                                .findByPlanTypeAndIsActiveTrue(PremiumPlan.PlanType.FREE_TIER)
                                .orElseThrow(() -> new RuntimeException("FREE_TIER plan not found"));
                UserSubscription freeSub = UserSubscription.builder()
                                .user(user)
                                .plan(freePlan)
                                .startDate(now)
                                .endDate(now.plusYears(100))
                                .isActive(true)
                                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                                .autoRenew(false)
                                .build();
                userSubscriptionRepository.save(freeSub);
        }

        @Override
        @Transactional
        public UserSubscriptionResponse ensureActiveSubscriptionOrFree(Long userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));
                LocalDateTime now = LocalDateTime.now();

                return userSubscriptionRepository.findByUserAndIsActiveTrue(user)
                                .filter(UserSubscription::isCurrentlyActive)
                                .map(this::convertToUserSubscriptionResponse)
                                .orElseGet(() -> {
                                        assignFreeTierIfMissing(userId);
                                        return userSubscriptionRepository.findByUserAndIsActiveTrue(user)
                                                        .map(this::convertToUserSubscriptionResponse)
                                                        .orElseThrow(() -> new RuntimeException(
                                                                        "Failed to assign Free Tier"));
                                });
        }

        private PremiumPlanResponse convertToPremiumPlanResponse(PremiumPlan plan) {
                return PremiumPlanResponse.builder()
                                .id(plan.getId())
                                .name(plan.getName())
                                .displayName(plan.getDisplayName())
                                .description(plan.getDescription())
                                .durationMonths(plan.getDurationMonths())
                                .price(plan.getPrice())
                                .currency(plan.getCurrency())
                                .planType(plan.getPlanType())
                                .studentPrice(plan.getStudentPrice())
                                .studentDiscountPercent(plan.getStudentDiscountPercent())
                                .features(plan.getFeatures() != null ? List.of(plan.getFeatures().split(","))
                                                : List.of())
                                .isActive(plan.getIsActive())
                                .maxSubscribers(plan.getMaxSubscribers())
                                .currentSubscribers((long) plan.getSubscriptions().size())
                                .availableForSubscription(plan.isAvailableForSubscription())
                                .build();
        }

        private UserSubscriptionResponse convertToUserSubscriptionResponse(UserSubscription subscription) {
                User user = subscription.getUser();
                String fullName = (user.getFirstName() != null ? user.getFirstName() : "") +
                                " " +
                                (user.getLastName() != null ? user.getLastName() : "");

                return UserSubscriptionResponse.builder()
                                .id(subscription.getId())
                                .userId(user.getId())
                                .userName(fullName.trim())
                                .userEmail(user.getEmail())
                                .userAvatarUrl(getUserAvatarUrl(user))
                                .plan(convertToPremiumPlanResponse(subscription.getPlan()))
                                .startDate(subscription.getStartDate())
                                .endDate(subscription.getEndDate())
                                .isActive(subscription.getIsActive())
                                .status(subscription.getStatus())
                                .isStudentSubscription(subscription.getIsStudentSubscription())
                                .autoRenew(subscription.getAutoRenew())
                                .paymentTransactionId(
                                                subscription.getPaymentTransaction() != null
                                                                ? subscription.getPaymentTransaction().getId()
                                                                : null)
                                .daysRemaining(subscription.getDaysRemaining())
                                .currentlyActive(subscription.isCurrentlyActive())
                                .cancellationReason(subscription.getCancellationReason())
                                .cancelledAt(subscription.getCancelledAt())
                                .createdAt(subscription.getCreatedAt())
                                .build();
        }

        @Override
        @Transactional
        public UserSubscriptionResponse purchaseWithWalletCash(Long userId, Long planId, boolean applyStudentDiscount) {
                log.info("💰 User {} purchasing premium plan {} with wallet cash", userId, planId);

                // 1. Validate user
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

                // 2. Validate plan
                PremiumPlan plan = premiumPlanRepository.findById(planId)
                                .filter(p -> p.getIsActive())
                                .orElseThrow(() -> new RuntimeException("Premium plan not found: " + planId));

                // 3. Check existing subscription
                Optional<UserSubscription> existingSubscription = userSubscriptionRepository
                                .findByUserAndIsActiveTrue(user);

                if (existingSubscription.isPresent()) {
                        PremiumPlan existingPlan = existingSubscription.get().getPlan();
                        if (existingPlan.getPlanType() != PremiumPlan.PlanType.FREE_TIER) {
                                throw new RuntimeException("User already has an active premium subscription");
                        }
                        // Deactivate FREE_TIER to allow upgrading
                        UserSubscription activeSub = existingSubscription.get();
                        activeSub.cancel("Upgrading from Free Tier via Wallet");
                        userSubscriptionRepository.save(activeSub);
                }

                // 4. Calculate price (with student discount if applicable)
                boolean isStudentEligible = applyStudentDiscount && isValidStudentEmail(user.getEmail());
                BigDecimal finalPrice = isStudentEligible ? plan.getStudentPrice() : plan.getPrice();

                log.info("💵 Plan price: {} VND (student discount: {})", finalPrice, isStudentEligible);

                // 5. Deduct cash from wallet using WalletService
                String purchaseDescription = String.format("Mua gói Premium: %s", plan.getDisplayName());
                try {
                        walletService.deductCash(userId, finalPrice, purchaseDescription,
                                        "PREMIUM_SUBSCRIPTION", planId.toString());
                } catch (Exception e) {
                        log.error("Failed to deduct wallet balance: {}", e.getMessage());
                        throw new RuntimeException("Insufficient wallet balance or payment failed: " + e.getMessage());
                }

                log.info("💳 Wallet payment processed successfully");

                // 8. Create and activate subscription immediately
                LocalDateTime startDate = LocalDateTime.now();
                LocalDateTime endDate = startDate.plusMonths(plan.getDurationMonths());

                UserSubscription subscription = UserSubscription.builder()
                                .user(user)
                                .plan(plan)
                                .startDate(startDate)
                                .endDate(endDate)
                                .isActive(true) // Activate immediately since payment is done
                                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                                .isStudentSubscription(isStudentEligible)
                                .autoRenew(false)
                                .build();

                subscription = userSubscriptionRepository.save(subscription);

                log.info("✅ Premium subscription activated for user {} via wallet payment", userId);

                // Send premium purchase success email
                premiumEmailService.sendPremiumPurchaseSuccessEmail(
                                user,
                                subscription,
                                finalPrice,
                                "WALLET");

                try {
                        notificationService.createNotification(
                                        user.getId(),
                                        "Đăng ký Premium thành công",
                                        "Bạn đã đăng ký gói Premium " + plan.getDisplayName() + " thành công bằng ví.",
                                        NotificationType.PREMIUM_PURCHASE,
                                        String.valueOf(subscription.getId()));
                } catch (Exception e) {
                        log.error("Failed to create notification for premium purchase: {}", e.getMessage());
                }

                return convertToUserSubscriptionResponse(subscription);
        }

        @Override
        @Transactional
        public void enableAutoRenewal(Long userId) {
                log.info("🔄 Enabling auto-renewal for user {}", userId);

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                var subscriptionOpt = userSubscriptionRepository.findByUserAndIsActiveTrue(user);
                if (subscriptionOpt.isEmpty()) {
                        throw new RuntimeException("No active subscription found");
                }

                UserSubscription subscription = subscriptionOpt.get();

                // Check if already enabled
                if (subscription.getAutoRenew()) {
                        throw new RuntimeException("Auto-renewal is already enabled");
                }

                // Enable auto-renewal
                subscription.setAutoRenew(true);
                userSubscriptionRepository.save(subscription);

                log.info("✅ Auto-renewal enabled for user {}. Next renewal before {}",
                                userId, subscription.getEndDate());
        }

        @Override
        @Transactional
        public void cancelAutoRenewal(Long userId) {
                log.info("🔄 Cancelling auto-renewal for user {}", userId);

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

                UserSubscription subscription = userSubscriptionRepository
                                .findByUserAndIsActiveTrue(user)
                                .orElseThrow(() -> new RuntimeException(
                                                "No active subscription found for user: " + userId));

                if (subscription.getPlan().getPlanType() == PremiumPlan.PlanType.FREE_TIER) {
                        throw new RuntimeException("Free tier does not have auto-renewal");
                }

                // Just turn off auto-renewal, keep subscription active until end date
                subscription.setAutoRenew(false);
                userSubscriptionRepository.save(subscription);

                log.info("✅ Auto-renewal cancelled for user {}. Subscription remains active until {}",
                                userId, subscription.getEndDate());

                notificationService.createNotification(
                                userId,
                                "Hủy gia hạn tự động",
                                "Bạn đã hủy gia hạn tự động gói Premium thành công. Gói của bạn vẫn có hiệu lực đến "
                                                + subscription.getEndDate().toLocalDate(),
                                NotificationType.PREMIUM_CANCEL,
                                String.valueOf(subscription.getId()));
        }

        @Override
        @Transactional
        public double cancelSubscriptionWithRefund(Long userId, String reason) {
                log.info("🔄 Processing subscription cancellation with refund for user {}", userId);

                // 1. Find user and active subscription
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

                UserSubscription subscription = userSubscriptionRepository
                                .findByUserAndIsActiveTrue(user)
                                .orElseThrow(() -> new RuntimeException(
                                                "No active subscription found for user: " + userId));

                // 2. Check if Free tier (cannot cancel/refund)
                if (subscription.getPlan().getPlanType() == PremiumPlan.PlanType.FREE_TIER) {
                        throw new RuntimeException("Cannot cancel Free tier subscription");
                }

                // 2.5. Check cancellation limit (max 1 time per month)
                String currentMonth = SubscriptionCancellation.getCurrentMonth();
                Long cancellationsThisMonth = cancellationRepository.countByUserAndCancellationMonth(user,
                                currentMonth);
                if (cancellationsThisMonth >= 1) {
                        throw new RuntimeException(
                                        "Bạn đã hủy gói Premium trong tháng này. Chỉ được phép hủy 1 lần/tháng. Vui lòng thử lại vào tháng sau.");
                }

                // 3. Calculate days since purchase
                LocalDateTime purchaseDate = subscription.getStartDate();
                LocalDateTime now = LocalDateTime.now();
                long hoursSincePurchase = Duration.between(purchaseDate, now).toHours();
                long daysSincePurchase = hoursSincePurchase / 24;

                // 4. Calculate refund percentage based on usage time
                int refundPercentage;
                if (hoursSincePurchase <= 24) {
                        refundPercentage = 100; // Within 24h: 100% refund
                } else if (daysSincePurchase <= 3) {
                        refundPercentage = 50; // 1-3 days: 50% refund
                } else {
                        refundPercentage = 0; // Over 3 days: No refund, just cancel auto-renewal
                }

                // 5. Calculate actual refund amount
                BigDecimal originalPrice = subscription.getPlan().getPrice();
                if (subscription.getIsStudentSubscription()) {
                        originalPrice = originalPrice.multiply(BigDecimal.valueOf(0.8)); // 20% student discount
                }

                BigDecimal refundAmount = originalPrice.multiply(BigDecimal.valueOf(refundPercentage))
                                .divide(BigDecimal.valueOf(100));

                // 6. Cancel subscription or just turn off auto-renewal
                if (refundPercentage > 0) {
                        // Full cancellation with refund
                        subscription.setIsActive(false);
                        subscription.setStatus(UserSubscription.SubscriptionStatus.CANCELLED);
                        subscription.setEndDate(now);

                        // Process refund to wallet
                        String refundDescription = String.format(
                                        "Hoàn tiền %d%% hủy gói Premium %s - Lý do: %s",
                                        refundPercentage,
                                        subscription.getPlan().getPlanType(),
                                        reason != null ? reason : "Không hài lòng");
                        String referenceId = "SUB_REFUND_" + subscription.getId() + "_" + System.currentTimeMillis();

                        walletService.processRefund(userId, refundAmount, refundDescription, referenceId);

                        // Assign Free tier back
                        assignFreeTierIfMissing(userId);

                        log.info("✅ Subscription cancelled with {}% refund ({} VND) for user {}",
                                        refundPercentage, refundAmount, userId);
                } else {
                        // No refund, just cancel auto-renewal
                        subscription.setAutoRenew(false);
                        log.info("⚠️ No refund (over 3 days). Auto-renewal cancelled. Subscription active until {}",
                                        subscription.getEndDate());
                }

                userSubscriptionRepository.save(subscription);

                // 7. Record cancellation
                SubscriptionCancellation cancellationRecord = SubscriptionCancellation.builder()
                                .user(user)
                                .subscription(subscription)
                                .cancellationMonth(currentMonth)
                                .refundPercentage(refundPercentage)
                                .refundAmount(refundAmount)
                                .daysSincePurchase(daysSincePurchase)
                                .reason(reason)
                                .cancellationType(refundPercentage > 0
                                                ? SubscriptionCancellation.CancellationType.CANCEL_WITH_REFUND
                                                : SubscriptionCancellation.CancellationType.CANCEL_AUTO_RENEWAL)
                                .build();

                cancellationRepository.save(cancellationRecord);
                log.info("📝 Recorded cancellation for user {} in month {}", userId, currentMonth);

                notificationService.createNotification(
                                userId,
                                "Hủy gói Premium",
                                "Bạn đã hủy gói Premium thành công. " + (refundPercentage > 0
                                                ? "Số tiền hoàn lại: " + refundAmount + " VNĐ"
                                                : "Gói của bạn sẽ hết hạn vào " + subscription.getEndDate().toLocalDate()),
                                NotificationType.PREMIUM_CANCEL,
                                String.valueOf(subscription.getId()));

                return refundAmount.doubleValue();
        }

        @Override
        public RefundEligibility getRefundEligibility(Long userId) {
                log.info("🔍 Checking refund eligibility for user {}", userId);

                User user = userRepository.findById(userId).orElse(null);
                if (user == null) {
                        return new RefundEligibility(false, 0, 0.0, 0, "User not found");
                }

                var subscriptionOpt = userSubscriptionRepository.findByUserAndIsActiveTrue(user);
                if (subscriptionOpt.isEmpty()) {
                        return new RefundEligibility(false, 0, 0.0, 0, "No active subscription");
                }

                UserSubscription subscription = subscriptionOpt.get();

                if (subscription.getPlan().getPlanType() == PremiumPlan.PlanType.FREE_TIER) {
                        return new RefundEligibility(false, 0, 0.0, 0, "Free tier cannot be refunded");
                }

                // CHECK CANCELLATION LIMIT FIRST
                String currentMonth = SubscriptionCancellation.getCurrentMonth();
                Long cancellationsThisMonth = cancellationRepository.countByUserAndCancellationMonth(user,
                                currentMonth);
                if (cancellationsThisMonth >= 1) {
                        throw new RuntimeException(
                                        "Bạn đã hủy gói Premium trong tháng này. Chỉ được phép hủy 1 lần/tháng. Vui lòng thử lại vào tháng sau.");
                }

                // Calculate time since purchase
                LocalDateTime purchaseDate = subscription.getStartDate();
                LocalDateTime now = LocalDateTime.now();
                long hoursSincePurchase = Duration.between(purchaseDate, now).toHours();
                long daysSincePurchase = hoursSincePurchase / 24;

                // Determine refund percentage
                int refundPercentage;
                String message;
                if (hoursSincePurchase <= 24) {
                        refundPercentage = 100;
                        message = "Eligible for 100% refund (within 24 hours)";
                } else if (daysSincePurchase <= 3) {
                        refundPercentage = 50;
                        message = "Eligible for 50% refund (1-3 days)";
                } else {
                        refundPercentage = 0;
                        message = "No refund available (over 3 days). Can only cancel auto-renewal.";
                }

                // Calculate refund amount
                BigDecimal originalPrice = subscription.getPlan().getPrice();
                if (subscription.getIsStudentSubscription()) {
                        originalPrice = originalPrice.multiply(BigDecimal.valueOf(0.8));
                }

                double refundAmount = originalPrice.multiply(BigDecimal.valueOf(refundPercentage))
                                .divide(BigDecimal.valueOf(100))
                                .doubleValue();

                return new RefundEligibility(true, refundPercentage, refundAmount, daysSincePurchase, message);
        }

        /**
         * Helper method to calculate end date based on start date and duration
         */
        private LocalDateTime calculateEndDate(LocalDateTime startDate, Integer durationMonths) {
                if (durationMonths == null || durationMonths <= 0) {
                        throw new IllegalArgumentException("Duration months must be positive");
                }
                return startDate.plusMonths(durationMonths);
        }

        // ==================== ADMIN METHODS ====================

        @Override
        @Transactional(readOnly = true)
        public org.springframework.data.domain.Page<UserSubscriptionResponse> getAllSubscriptionsAdmin(
                        String status,
                        Long userId,
                        Long planId,
                        Pageable pageable) {
                log.info("Admin fetching all subscriptions - status: {}, userId: {}, planId: {}", status, userId,
                                planId);

                // For now, return all subscriptions with pagination
                // TODO: Add filtering by status, userId, planId
                org.springframework.data.domain.Page<UserSubscription> subscriptions = userSubscriptionRepository
                                .findAll(pageable);

                return subscriptions.map(this::convertToUserSubscriptionResponse);
        }

        @Override
        @Transactional(readOnly = true)
        public Optional<UserSubscriptionResponse> getSubscriptionByIdAdmin(Long id) {
                log.info("Admin fetching subscription detail for id: {}", id);

                return userSubscriptionRepository.findById(id)
                                .map(this::convertToUserSubscriptionResponse);
        }

        @Override
        @Transactional(readOnly = true)
        public Map<String, Object> getPremiumStatistics() {
                log.info("Admin fetching premium statistics");

                List<UserSubscription> allSubscriptions = userSubscriptionRepository.findAll();

                // Count Free Tier subscribers separately
                long freeSubscribers = allSubscriptions.stream()
                                .filter(s -> s.getStatus() == UserSubscription.SubscriptionStatus.ACTIVE)
                                .filter(s -> s.getPlan().getPlanType() == PremiumPlan.PlanType.FREE_TIER)
                                .count();

                // Filter out FREE_TIER for Premium statistics to reflect actual business
                // performance
                long totalPremiumSubscriptions = allSubscriptions.stream()
                                .filter(s -> s.getPlan().getPlanType() != PremiumPlan.PlanType.FREE_TIER)
                                .count();

                long activePremiumSubscriptions = allSubscriptions.stream()
                                .filter(s -> s.getStatus() == UserSubscription.SubscriptionStatus.ACTIVE)
                                .filter(s -> s.getPlan().getPlanType() != PremiumPlan.PlanType.FREE_TIER)
                                .count();

                long expiredPremiumSubscriptions = allSubscriptions.stream()
                                .filter(s -> s.getStatus() == UserSubscription.SubscriptionStatus.EXPIRED)
                                .filter(s -> s.getPlan().getPlanType() != PremiumPlan.PlanType.FREE_TIER)
                                .count();

                long cancelledPremiumSubscriptions = allSubscriptions.stream()
                                .filter(s -> s.getStatus() == UserSubscription.SubscriptionStatus.CANCELLED)
                                .filter(s -> s.getPlan().getPlanType() != PremiumPlan.PlanType.FREE_TIER)
                                .count();

                // Calculate total revenue from active subscriptions (Free Tier has price 0 so
                // it doesn't affect sum, but good to be explicit)
                double totalRevenue = allSubscriptions.stream()
                                .filter(s -> s.getStatus() == UserSubscription.SubscriptionStatus.ACTIVE)
                                .mapToDouble(s -> s.getPlan().getPrice().doubleValue())
                                .sum();

                Map<String, Object> stats = new HashMap<>();
                stats.put("totalSubscriptions", totalPremiumSubscriptions);
                stats.put("activeSubscriptions", activePremiumSubscriptions);
                stats.put("expiredSubscriptions", expiredPremiumSubscriptions);
                stats.put("cancelledSubscriptions", cancelledPremiumSubscriptions);
                stats.put("totalRevenue", totalRevenue);
                stats.put("freeSubscribers", freeSubscribers); // Add extra field for clarity

                return stats;
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
}
