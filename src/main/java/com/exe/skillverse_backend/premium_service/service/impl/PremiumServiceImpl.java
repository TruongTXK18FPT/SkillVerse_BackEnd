package com.exe.skillverse_backend.premium_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.impl.NotificationServiceImpl;
import com.exe.skillverse_backend.parent_service.entity.ParentStudentLink;
import com.exe.skillverse_backend.parent_service.entity.enums.LinkStatus;
import com.exe.skillverse_backend.parent_service.repository.ParentStudentLinkRepository;
import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;
import com.exe.skillverse_backend.payment_service.repository.PaymentTransactionRepository;
import com.exe.skillverse_backend.premium_service.dto.request.CreateSubscriptionRequest;
import com.exe.skillverse_backend.premium_service.dto.response.PremiumPlanResponse;
import com.exe.skillverse_backend.premium_service.dto.response.UserSubscriptionResponse;
import com.exe.skillverse_backend.premium_service.entity.PremiumPlan;
import com.exe.skillverse_backend.premium_service.entity.SubscriptionCancellation;
import com.exe.skillverse_backend.premium_service.entity.UserSubscription;
import com.exe.skillverse_backend.premium_service.repository.PremiumPlanRepository;
import com.exe.skillverse_backend.premium_service.repository.SubscriptionCancellationRepository;
import com.exe.skillverse_backend.premium_service.repository.UserSubscriptionRepository;
import com.exe.skillverse_backend.premium_service.service.PremiumEmailService;
import com.exe.skillverse_backend.premium_service.service.PremiumService;
import com.exe.skillverse_backend.user_service.service.UserProfileService;
import com.exe.skillverse_backend.wallet_service.entity.Wallet;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        private final NotificationServiceImpl notificationService;
        private final ParentStudentLinkRepository parentStudentLinkRepository;
        private final ObjectMapper objectMapper;

        private static final List<String> STUDENT_EMAIL_DOMAINS = List.of(
                        ".edu", ".edu.vn", ".ac.uk", "university.", "student.", ".edu.au");

        @Override
        @Transactional(readOnly = true)
        public List<PremiumPlanResponse> getAvailablePlans() {
                log.info("Fetching all available premium plans");
                return premiumPlanRepository.findByIsActiveTrueOrderByPrice()
                                .stream()
                                .map(this::convertToPremiumPlanResponse)
                                .collect(Collectors.toList());
        }

        @Override
        @Transactional(readOnly = true)
        public List<PremiumPlanResponse> getAvailablePlansByTargetRole(
                        PremiumPlan.TargetRole targetRole,
                        boolean includeFreeTier) {
                log.info("Fetching premium plans for target role {} (includeFreeTier: {})",
                                targetRole, includeFreeTier);

                return premiumPlanRepository.findByIsActiveTrueOrderByPrice()
                                .stream()
                                .filter(plan -> matchesTargetRole(plan, targetRole, includeFreeTier))
                                .map(this::convertToPremiumPlanResponse)
                                .collect(Collectors.toList());
        }

        @Override
        @Transactional(readOnly = true)
        public Optional<PremiumPlanResponse> getPlanById(Long planId) {
                log.info("Fetching premium plan with ID: {}", planId);
                return premiumPlanRepository.findById(planId)
                                .filter(plan -> plan.getIsActive())
                                .map(this::convertToPremiumPlanResponse);
        }

        @Override
        @Transactional(readOnly = true)
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

                if (request.getTargetUserId() != null) {
                        Long targetId = request.getTargetUserId();
                        log.info("Parent {} is buying for child {}", userId, targetId);
                        
                        ParentStudentLink link = parentStudentLinkRepository.findByParentIdAndStudentId(userId, targetId)
                                .orElseThrow(() -> new RuntimeException("No link found between parent and student"));
                                
                        if (link.getStatus() != LinkStatus.ACTIVE) {
                                throw new RuntimeException("Link is not active");
                        }
                        
                        user = userRepository.findById(targetId)
                                .orElseThrow(() -> new RuntimeException("Target student not found"));
                }

                PremiumPlan plan = premiumPlanRepository.findById(request.getPlanId())
                                .filter(p -> p.getIsActive())
                                .orElseThrow(() -> new RuntimeException(
                                                "Premium plan not found: " + request.getPlanId()));

                validatePlanEligibility(user, plan);

                Optional<UserSubscription> existingSubscription = userSubscriptionRepository
                                .findCurrentActiveSubscription(user);

                if (existingSubscription.isPresent()) {
                        PremiumPlan existingPlan = existingSubscription.get().getPlan();
                        if (existingPlan.getPlanType() != PremiumPlan.PlanType.FREE_TIER) {
                                throw new RuntimeException("User already has an active subscription");
                        }
                        // SUSPEND (not cancel) FREE_TIER - will reactivate when premium expires
                        UserSubscription freeTierSub = existingSubscription.get();
                        freeTierSub.suspend("Upgrading to Premium - will reactivate when premium expires");
                        userSubscriptionRepository.save(freeTierSub);
                }

                boolean isStudentEligible = request.getApplyStudentDiscount() &&
                                isValidStudentEmail(user.getEmail());

                LocalDateTime startDate = LocalDateTime.now();
                // Use plan's actual duration (will be recalculated on activation)
                int durationMonths = plan.getDurationMonths() != null && plan.getDurationMonths() > 0
                                ? plan.getDurationMonths() : 1;
                LocalDateTime endDate = startDate.plusMonths(durationMonths);

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
        @Transactional
        public Optional<UserSubscriptionResponse> getCurrentSubscription(Long userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));
                Optional<UserSubscription> activeSub = userSubscriptionRepository.findCurrentActiveSubscription(user);
                if (activeSub.isPresent()) {
                        return activeSub.map(this::convertToUserSubscriptionResponse);
                }

                // Auto-recovery: try to activate PENDING subscriptions with completed payments
                boolean recovered = tryRecoverPendingSubscriptions(userId);
                if (recovered) {
                        // Re-query after recovery
                        return userSubscriptionRepository.findCurrentActiveSubscription(user)
                                        .map(this::convertToUserSubscriptionResponse);
                }

                return Optional.empty();
        }

        @Override
        @Transactional(readOnly = true)
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
                UserSubscription subscription = userSubscriptionRepository.findCurrentActiveSubscription(user)
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

                if (paymentTransaction.getType() != PaymentTransaction.PaymentType.PREMIUM_SUBSCRIPTION) {
                        throw new RuntimeException("Invalid payment type for premium activation");
                }

                Long payerUserId = paymentTransaction.getUser().getId();
                Long beneficiaryUserId = subscription.getUser().getId();
                if (!payerUserId.equals(beneficiaryUserId)) {
                        ParentStudentLink link = parentStudentLinkRepository
                                        .findByParentIdAndStudentId(payerUserId, beneficiaryUserId)
                                        .orElseThrow(() -> new RuntimeException("Payment user is not allowed for this subscription"));
                        if (link.getStatus() != LinkStatus.ACTIVE) {
                                throw new RuntimeException("Parent-student link is not active");
                        }
                }

                // Recalculate dates from activation time to give user full duration
                LocalDateTime activationTime = LocalDateTime.now();
                PremiumPlan plan = subscription.getPlan();
                int durationMonths = (plan != null && plan.getDurationMonths() != null && plan.getDurationMonths() > 0)
                                ? plan.getDurationMonths() : 1;
                subscription.setStartDate(activationTime);
                subscription.setEndDate(activationTime.plusMonths(durationMonths));
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
                                .findSubscriptionsForAutoRenewal(now, renewalWindow, UserSubscription.SubscriptionStatus.ACTIVE);

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

                        // Send auto-renewal success email
                        premiumEmailService.sendAutoRenewalSuccessEmail(user, subscription, price);

                } catch (Exception e) {
                        log.error("❌ Auto-renewal failed for subscription {}: Insufficient balance. Disabling auto-renewal.",
                                        subscription.getId());

                        // Disable auto-renewal if payment fails
                        subscription.setAutoRenew(false);
                        userSubscriptionRepository.save(subscription);

                        // TODO: Send notification to user about failed auto-renewal
                }
        }

        private static final int BATCH_SIZE = 500;

        @Override
        @Scheduled(cron = "0 0 * * * ?")
        @Transactional
        public void processExpiredSubscriptions() {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime freeTierEndDate = now.plusYears(100);
                
                // Step 1: Bulk update expired subscriptions (single query)
                int expiredCount = userSubscriptionRepository.markExpiredSubscriptions(now);
                log.info("Marked {} subscriptions as expired at {}", expiredCount, now);
                
                // Step 2: Early exit if nothing expired - skip unnecessary processing
                if (expiredCount == 0) {
                        log.debug("No subscriptions expired this hour, skipping Free Tier reactivation");
                        return;
                }
                
                // Step 3: Find FREE_TIER plan once (only needed for new subscriptions)
                Optional<PremiumPlan> freePlanOpt = premiumPlanRepository
                                .findByPlanTypeAndIsActiveTrue(PremiumPlan.PlanType.FREE_TIER);
                
                if (freePlanOpt.isEmpty()) {
                        log.error("FREE_TIER plan not found! Cannot assign fallback subscriptions.");
                        return;
                }
                
                PremiumPlan freePlan = freePlanOpt.get();
                
                // Step 4: Find users without active subscription (premium just expired)
                List<Long> userIdsWithoutSub = userSubscriptionRepository
                                .findUserIdsWithoutActiveSubscription(now);
                
                if (userIdsWithoutSub.isEmpty()) {
                        log.info("All users have active subscriptions, no Free Tier reactivation needed");
                        return;
                }
                
                log.info("Found {} users without active subscription, reactivating Free Tier", 
                                userIdsWithoutSub.size());
                
                // Step 5: [CHATGPT MODEL] Reactivate SUSPENDED Free Tier (primary path)
                int totalReactivated = 0;
                int totalCreated = 0;
                
                // Process in chunks for memory efficiency
                for (int i = 0; i < userIdsWithoutSub.size(); i += BATCH_SIZE) {
                        int endIdx = Math.min(i + BATCH_SIZE, userIdsWithoutSub.size());
                        List<Long> batch = userIdsWithoutSub.subList(i, endIdx);
                        
                        // Primary: Batch reactivate SUSPENDED FREE_TIER (most common case after upgrade expires)
                        int reactivated = userSubscriptionRepository.batchReactivateSuspendedFreeTier(batch, now);
                        totalReactivated += reactivated;
                        
                        // Fallback: For legacy users without Free Tier, create new one
                        List<Long> usersWithExisting = userSubscriptionRepository.findUserIdsWithExistingFreeTier(batch);
                        List<Long> usersNeedingNew = batch.stream()
                                .filter(id -> !usersWithExisting.contains(id))
                                .toList();
                        
                        for (Long userId : usersNeedingNew) {
                                try {
                                        createNewFreeTierSubscription(userId, freePlan, now, freeTierEndDate);
                                        totalCreated++;
                                } catch (Exception e) {
                                        log.warn("Failed to create Free Tier for user {}: {}", userId, e.getMessage());
                                }
                        }
                        
                        log.debug("Batch {}-{}: {} reactivated, {} created", i, endIdx, reactivated, usersNeedingNew.size());
                }
                
                log.info("Processed expired subscriptions: {} expired, {} Free Tier reactivated, {} created at {}", 
                                expiredCount, totalReactivated, totalCreated, now);
        }

        /**
         * Create new FREE_TIER subscription using reference only (no full entity load)
         */
        private void createNewFreeTierSubscription(Long userId, PremiumPlan freePlan, 
                        LocalDateTime now, LocalDateTime endDate) {
                User userRef = userRepository.getReferenceById(userId);
                
                UserSubscription freeSub = UserSubscription.builder()
                        .user(userRef)
                        .plan(freePlan)
                        .startDate(now)
                        .endDate(endDate)
                        .isActive(true)
                        .status(UserSubscription.SubscriptionStatus.ACTIVE)
                        .autoRenew(false)
                        .build();
                
                userSubscriptionRepository.save(freeSub);
        }
        
        /**
         * [OPTIMIZED] Internal method to assign Free Tier without loading User entity.
         * Reactivates existing Free Tier if available, otherwise creates new.
         */
        private void assignFreeTierByUserId(Long userId, PremiumPlan freePlan, LocalDateTime now) {
                // Check if user already has a Free Tier subscription (active or inactive)
                Optional<UserSubscription> existingFreeTier = userSubscriptionRepository
                                .findFreeTierSubscriptionByUserId(userId);
                
                if (existingFreeTier.isPresent()) {
                        // Reactivate existing Free Tier instead of creating duplicate
                        UserSubscription freeSub = existingFreeTier.get();
                        if (!freeSub.getIsActive()) {
                                freeSub.setIsActive(true);
                                freeSub.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
                                freeSub.setStartDate(now);
                                freeSub.setEndDate(now.plusYears(100));
                                userSubscriptionRepository.save(freeSub);
                                log.debug("Reactivated Free Tier for user {}", userId);
                        }
                        // Already active - do nothing
                        return;
                }
                
                // Create new Free Tier subscription
                // Load user reference only (not full entity with EAGER collections)
                User userRef = userRepository.getReferenceById(userId);
                
                UserSubscription freeSub = UserSubscription.builder()
                                .user(userRef)
                                .plan(freePlan)
                                .startDate(now)
                                .endDate(now.plusYears(100))
                                .isActive(true)
                                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                                .autoRenew(false)
                                .build();
                
                userSubscriptionRepository.save(freeSub);
                log.debug("Created new Free Tier for user {}", userId);
        }

        @Override
        @Transactional
        public void assignFreeTierIfMissing(Long userId) {
                LocalDateTime now = LocalDateTime.now();
                
                // [OPTIMIZED] Check by user ID instead of loading full User entity
                boolean hasActive = userSubscriptionRepository.hasActiveSubscriptionByUserId(userId, now);
                if (hasActive) {
                        log.debug("User {} already has active subscription, skipping Free Tier", userId);
                        return;
                }
                
                // Find FREE_TIER plan
                PremiumPlan freePlan = premiumPlanRepository
                                .findByPlanTypeAndIsActiveTrue(PremiumPlan.PlanType.FREE_TIER)
                                .orElseThrow(() -> new RuntimeException("FREE_TIER plan not found"));
                
                // Delegate to optimized internal method
                assignFreeTierByUserId(userId, freePlan, now);
                log.info("Assigned Free Tier to user {}", userId);
        }

        @Override
        @Transactional
        public UserSubscriptionResponse ensureActiveSubscriptionOrFree(Long userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                return userSubscriptionRepository.findCurrentActiveSubscription(user)
                                .map(this::convertToUserSubscriptionResponse)
                                .orElseGet(() -> {
                                        assignFreeTierIfMissing(userId);
                                        return userSubscriptionRepository.findCurrentActiveSubscription(user)
                                                        .map(this::convertToUserSubscriptionResponse)
                                                        .orElseThrow(() -> new RuntimeException(
                                                                        "Failed to assign Free Tier"));
                                });
        }

        private PremiumPlanResponse convertToPremiumPlanResponse(PremiumPlan plan) {
                // Use query instead of lazy-loading subscriptions to prevent N+1
                Long currentSubscribers = premiumPlanRepository.countActiveSubscriptions(plan);
                
                return PremiumPlanResponse.builder()
                                .id(plan.getId())
                                .name(plan.getName())
                                .displayName(plan.getDisplayName())
                                .description(plan.getDescription())
                                .durationMonths(plan.getDurationMonths())
                                .price(plan.getPrice())
                                .currency(plan.getCurrency())
                                .planType(plan.getPlanType())
                                .targetRole(plan.getTargetRole())
                                .studentPrice(plan.getStudentPrice())
                                .studentDiscountPercent(plan.getStudentDiscountPercent())
                                .features(parsePlanFeatures(plan.getFeatures()))
                                .isActive(plan.getIsActive())
                                .maxSubscribers(plan.getMaxSubscribers())
                                .currentSubscribers(currentSubscribers)
                                .availableForSubscription(plan.getMaxSubscribers() == null ||
                                                currentSubscribers < plan.getMaxSubscribers())
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
                // Delegate to the overloaded method with null targetUserId (self-purchase)
                return purchaseWithWalletCash(userId, planId, applyStudentDiscount, null);
        }

        @Override
        @Transactional
        public UserSubscriptionResponse purchaseWithWalletCash(Long buyerId, Long planId, boolean applyStudentDiscount, Long targetUserId) {
                log.info("💰 User {} purchasing premium plan {} with wallet cash (target: {})", buyerId, planId, targetUserId);

                // 1. Validate buyer
                User buyer = userRepository.findById(buyerId)
                                .orElseThrow(() -> new RuntimeException("Buyer not found with ID: " + buyerId));

                // 2. Determine the actual recipient
                User recipient;
                if (targetUserId != null && !targetUserId.equals(buyerId)) {
                        // This is a gift purchase - validate the link
                        log.info("🎁 Parent {} is gifting premium to child {}", buyerId, targetUserId);
                        
                        ParentStudentLink link = parentStudentLinkRepository.findByParentIdAndStudentId(buyerId, targetUserId)
                                .orElseThrow(() -> new RuntimeException("Không tìm thấy liên kết giữa phụ huynh và học sinh. Vui lòng kết nối trước khi mua."));
                                
                        if (link.getStatus() != LinkStatus.ACTIVE) {
                                throw new RuntimeException("Liên kết chưa được kích hoạt. Học sinh cần chấp nhận lời mời kết nối.");
                        }
                        
                        recipient = userRepository.findById(targetUserId)
                                .orElseThrow(() -> new RuntimeException("Không tìm thấy học sinh với ID: " + targetUserId));
                } else {
                        // Self-purchase
                        recipient = buyer;
                }

                // 2. Validate plan
                PremiumPlan plan = premiumPlanRepository.findById(planId)
                                .filter(p -> p.getIsActive())
                                .orElseThrow(() -> new RuntimeException("Premium plan not found: " + planId));

                validatePlanEligibility(recipient, plan);

                // 3. Check existing subscription for RECIPIENT
                Optional<UserSubscription> existingSubscription = userSubscriptionRepository
                                .findCurrentActiveSubscription(recipient);

                if (existingSubscription.isPresent()) {
                        PremiumPlan existingPlan = existingSubscription.get().getPlan();
                        if (existingPlan.getPlanType() != PremiumPlan.PlanType.FREE_TIER) {
                                throw new RuntimeException("Người nhận đã có gói Premium đang hoạt động");
                        }
                        // SUSPEND (not cancel) FREE_TIER - will reactivate when premium expires
                        UserSubscription freeTierSub = existingSubscription.get();
                        freeTierSub.suspend("Upgrading to Premium via Wallet" + (targetUserId != null ? " (Gift from " + buyerId + ")" : "") + " - will reactivate when premium expires");
                        userSubscriptionRepository.save(freeTierSub);
                }

                // 4. Calculate price (with student discount if applicable - based on RECIPIENT's email)
                boolean isStudentEligible = applyStudentDiscount && isValidStudentEmail(recipient.getEmail());
                BigDecimal finalPrice = isStudentEligible ? plan.getStudentPrice() : plan.getPrice();

                log.info("💵 Plan price: {} VND (student discount: {})", finalPrice, isStudentEligible);

                // 5. Deduct cash from BUYER's wallet
                String purchaseDescription = targetUserId != null 
                        ? String.format("Mua gói Premium: %s cho %s %s", plan.getDisplayName(), recipient.getFirstName(), recipient.getLastName())
                        : String.format("Mua gói Premium: %s", plan.getDisplayName());
                try {
                        walletService.deductCash(buyerId, finalPrice, purchaseDescription,
                                        "PREMIUM_SUBSCRIPTION", planId.toString());
                } catch (Exception e) {
                        log.error("Failed to deduct wallet balance: {}", e.getMessage());
                        throw new RuntimeException("Số dư ví không đủ hoặc thanh toán thất bại: " + e.getMessage());
                }

                log.info("💳 Wallet payment processed successfully");

                // 8. Create and activate subscription for RECIPIENT
                LocalDateTime startDate = LocalDateTime.now();
                LocalDateTime endDate = startDate.plusMonths(plan.getDurationMonths());

                UserSubscription subscription = UserSubscription.builder()
                                .user(recipient)
                                .plan(plan)
                                .startDate(startDate)
                                .endDate(endDate)
                                .isActive(true) // Activate immediately since payment is done
                                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                                .isStudentSubscription(isStudentEligible)
                                .autoRenew(false)
                                .build();

                subscription = userSubscriptionRepository.save(subscription);

                log.info("✅ Premium subscription activated for user {} via wallet payment (buyer: {})", recipient.getId(), buyerId);

                // Send premium purchase success email to RECIPIENT
                premiumEmailService.sendPremiumPurchaseSuccessEmail(
                                recipient,
                                subscription,
                                finalPrice,
                                "WALLET");

                try {
                        // Notify recipient
                        String notifyMessage = targetUserId != null 
                                ? "Bạn đã được " + buyer.getFirstName() + " " + buyer.getLastName() + " tặng gói Premium " + plan.getDisplayName() + "!"
                                : "Bạn đã đăng ký gói Premium " + plan.getDisplayName() + " thành công bằng ví.";
                        
                        notificationService.createNotification(
                                        recipient.getId(),
                                        "Đăng ký Premium thành công",
                                        notifyMessage,
                                        NotificationType.PREMIUM_PURCHASE,
                                        String.valueOf(subscription.getId()));
                                        
                        // Also notify buyer if gift purchase
                        if (targetUserId != null && !targetUserId.equals(buyerId)) {
                                notificationService.createNotification(
                                        buyerId,
                                        "Tặng Premium thành công",
                                        "Bạn đã tặng gói Premium " + plan.getDisplayName() + " cho " + recipient.getFirstName() + " " + recipient.getLastName() + " thành công!",
                                        NotificationType.PREMIUM_PURCHASE,
                                        String.valueOf(subscription.getId()));
                        }
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

                var subscriptionOpt = userSubscriptionRepository.findCurrentActiveSubscription(user);
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
                                .findCurrentActiveSubscription(user)
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
                                .findCurrentActiveSubscription(user)
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
                                                : "Gói của bạn sẽ hết hạn vào "
                                                                + subscription.getEndDate().toLocalDate()),
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

                var subscriptionOpt = userSubscriptionRepository.findCurrentActiveSubscription(user);
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
        public Page<UserSubscriptionResponse> getAllSubscriptionsAdmin(
                        String status,
                        Long userId,
                        Long planId,
                        Pageable pageable) {
                log.info("Admin fetching all subscriptions - status: {}, userId: {}, planId: {}", status, userId,
                                planId);

                // For now, return all subscriptions with pagination
                // TODO: Add filtering by status, userId, planId
                Page<UserSubscription> subscriptions = userSubscriptionRepository
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

        @Override
        @Transactional
        public boolean tryRecoverPendingSubscriptions(Long userId) {
                log.info("Checking for recoverable PENDING subscriptions for user {}", userId);

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                // Find all PENDING subscriptions for this user (any plan type)
                List<UserSubscription> pendingSubs = userSubscriptionRepository
                                .findPendingRecruiterSubscriptions(userId);

                // Also check general PENDING subscriptions via subscription history
                List<UserSubscription> allUserSubs = userSubscriptionRepository
                                .findByUserOrderByCreatedAtDesc(user, Pageable.unpaged())
                                .getContent()
                                .stream()
                                .filter(s -> s.getStatus() == UserSubscription.SubscriptionStatus.PENDING)
                                .toList();

                // Combine both lists, deduplicate by ID
                Set<Long> seenIds = new HashSet<>();
                List<UserSubscription> allPending = new ArrayList<>();
                for (UserSubscription s : pendingSubs) {
                        if (seenIds.add(s.getId())) allPending.add(s);
                }
                for (UserSubscription s : allUserSubs) {
                        if (seenIds.add(s.getId())) allPending.add(s);
                }

                if (allPending.isEmpty()) {
                        log.info("No PENDING subscriptions found for user {}", userId);
                        return false;
                }

                // Find completed PREMIUM_SUBSCRIPTION payments for this user
                List<PaymentTransaction> completedPayments = paymentTransactionRepository
                                .findByUserAndType(user, PaymentTransaction.PaymentType.PREMIUM_SUBSCRIPTION)
                                .stream()
                                .filter(p -> p.getStatus() == PaymentTransaction.PaymentStatus.COMPLETED)
                                .toList();

                if (completedPayments.isEmpty()) {
                        log.info("No COMPLETED premium payments found for user {}", userId);
                        return false;
                }

                // Try to match pending subscriptions with completed payments via metadata
                for (PaymentTransaction payment : completedPayments) {
                        String metadata = payment.getMetadata();
                        if (metadata == null || metadata.isEmpty()) continue;

                        try {
                                JsonNode node = objectMapper.readTree(metadata);
                                JsonNode subIdNode = node.get("subscriptionId");
                                if (subIdNode == null || subIdNode.isNull()) continue;

                                Long subscriptionId = subIdNode.asLong();

                                // Check if this subscription ID is in our pending list
                                for (UserSubscription pendingSub : allPending) {
                                        if (pendingSub.getId().equals(subscriptionId)) {
                                                log.info("Found matching PENDING subscription {} with COMPLETED payment {}. Activating...",
                                                                subscriptionId, payment.getInternalReference());
                                                try {
                                                        activateSubscription(subscriptionId, payment.getInternalReference());
                                                        log.info("Successfully auto-recovered subscription {} for user {}",
                                                                        subscriptionId, userId);
                                                        return true;
                                                } catch (Exception e) {
                                                        log.error("Failed to auto-recover subscription {}: {}",
                                                                        subscriptionId, e.getMessage());
                                                }
                                        }
                                }
                        } catch (Exception e) {
                                log.warn("Failed to parse metadata for payment {}: {}",
                                                payment.getInternalReference(), e.getMessage());
                        }
                }

                log.info("No recoverable subscriptions found for user {}", userId);
                return false;
        }

        private void validatePlanEligibility(User recipient, PremiumPlan plan) {
                if (recipient == null || plan == null || plan.getPlanType() == PremiumPlan.PlanType.FREE_TIER) {
                        return;
                }

                boolean recruiterUser = recipient.getPrimaryRole() == PrimaryRole.RECRUITER;
                boolean recruiterPlan = isRecruiterPlan(plan);

                if (recruiterUser && !recruiterPlan) {
                        throw new RuntimeException("Tài khoản Recruiter chỉ có thể đăng ký gói dành cho recruiter.");
                }

                if (!recruiterUser && recruiterPlan) {
                        throw new RuntimeException("Gói này chỉ dành cho tài khoản Recruiter.");
                }
        }

        private boolean isRecruiterPlan(PremiumPlan plan) {
                if (plan.getPlanType() == PremiumPlan.PlanType.RECRUITER_PRO
                                || plan.getTargetRole() == PremiumPlan.TargetRole.RECRUITER) {
                        return true;
                }

                String planName = plan.getName();
                return planName != null && planName.toLowerCase().startsWith("recruiter_");
        }

        private boolean matchesTargetRole(
                        PremiumPlan plan,
                        PremiumPlan.TargetRole targetRole,
                        boolean includeFreeTier) {
                if (plan == null || !Boolean.TRUE.equals(plan.getIsActive())) {
                        return false;
                }

                if (targetRole == null) {
                        return true;
                }

                if (includeFreeTier && plan.getPlanType() == PremiumPlan.PlanType.FREE_TIER) {
                        return true;
                }

                if (plan.getTargetRole() == targetRole) {
                        return true;
                }

                return targetRole == PremiumPlan.TargetRole.RECRUITER && isRecruiterPlan(plan);
        }

        private List<String> parsePlanFeatures(String rawFeatures) {
                if (rawFeatures == null || rawFeatures.isBlank()) {
                        return List.of();
                }

                try {
                        return objectMapper.readValue(rawFeatures, new TypeReference<List<String>>() {
                        });
                } catch (Exception e) {
                        log.warn("Failed to parse premium plan features as JSON: {}", e.getMessage());
                        return java.util.Arrays.stream(rawFeatures
                                        .replace("[", "")
                                        .replace("]", "")
                                        .replace("\"", "")
                                        .split(","))
                                        .map(String::trim)
                                        .filter(feature -> !feature.isBlank())
                                        .toList();
                }
        }
}
