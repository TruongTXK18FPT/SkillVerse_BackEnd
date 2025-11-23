package com.exe.skillverse_backend.premium_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;
import com.exe.skillverse_backend.payment_service.repository.PaymentTransactionRepository;
import com.exe.skillverse_backend.premium_service.dto.request.CreateSubscriptionRequest;
import com.exe.skillverse_backend.premium_service.dto.response.PremiumPlanResponse;
import com.exe.skillverse_backend.premium_service.dto.response.UserSubscriptionResponse;
import com.exe.skillverse_backend.premium_service.entity.PremiumPlan;
import com.exe.skillverse_backend.premium_service.entity.UserSubscription;
import com.exe.skillverse_backend.premium_service.repository.PremiumPlanRepository;
import com.exe.skillverse_backend.premium_service.repository.UserSubscriptionRepository;
import com.exe.skillverse_backend.premium_service.service.PremiumService;
import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;
import com.exe.skillverse_backend.wallet_service.repository.WalletRepository;
import com.exe.skillverse_backend.wallet_service.repository.WalletTransactionRepository;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
        private final WalletRepository walletRepository;
        private final WalletTransactionRepository walletTransactionRepository;

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

                return userSubscriptionRepository.save(subscription);
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
                return UserSubscriptionResponse.builder()
                                .id(subscription.getId())
                                .userId(subscription.getUser().getId())
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

                // 5. Get wallet and check balance
                com.exe.skillverse_backend.wallet_service.entity.Wallet wallet = walletRepository
                                .findByUserIdWithLock(userId)
                                .orElseThrow(() -> new RuntimeException("Wallet not found for user: " + userId));

                if (!wallet.hasAvailableCash(finalPrice)) {
                        BigDecimal available = wallet.getAvailableCashBalance();
                        BigDecimal needed = finalPrice.subtract(available);
                        throw new RuntimeException(String.format(
                                        "Insufficient wallet balance. Available: %,.0f VND, Required: %,.0f VND, Need to deposit: %,.0f VND",
                                        available, finalPrice, needed));
                }

                // 6. Deduct cash from wallet
                wallet.deductCash(finalPrice);
                walletRepository.save(wallet);

                // 7. Create wallet transaction record
                WalletTransaction walletTransaction = WalletTransaction.builder()
                                .wallet(wallet)
                                .transactionType(WalletTransaction.TransactionType.PURCHASE_PREMIUM)
                                .currencyType(WalletTransaction.CurrencyType.CASH)
                                .cashAmount(finalPrice)
                                .cashBalanceAfter(wallet.getCashBalance())
                                .description(String.format("Mua gói Premium: %s", plan.getDisplayName()))
                                .referenceType("PREMIUM_SUBSCRIPTION")
                                .referenceId(planId.toString())
                                .status(WalletTransaction.TransactionStatus.COMPLETED)
                                .build();
                
                walletTransaction = walletTransactionRepository.save(walletTransaction);
                log.info("💳 Wallet transaction created: {}", walletTransaction.getTransactionId());

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

                return convertToUserSubscriptionResponse(subscription);
        }
}
