package com.exe.skillverse_backend.premium_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.impl.NotificationServiceImpl;
import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;
import com.exe.skillverse_backend.payment_service.repository.PaymentTransactionRepository;
import com.exe.skillverse_backend.premium_service.constants.PremiumConstants;
import com.exe.skillverse_backend.premium_service.dto.response.PremiumPlanResponse;
import com.exe.skillverse_backend.premium_service.dto.response.SubscriptionCheckoutPreviewResponse;
import com.exe.skillverse_backend.premium_service.dto.response.UserSubscriptionResponse;
import com.exe.skillverse_backend.premium_service.entity.PremiumPlan;
import com.exe.skillverse_backend.premium_service.entity.SubscriptionCancellation;
import com.exe.skillverse_backend.premium_service.entity.UserSubscription;
import com.exe.skillverse_backend.premium_service.repository.PremiumPlanRepository;
import com.exe.skillverse_backend.premium_service.repository.SubscriptionCancellationRepository;
import com.exe.skillverse_backend.premium_service.repository.UserSubscriptionRepository;
import com.exe.skillverse_backend.premium_service.service.PremiumEmailService;
import com.exe.skillverse_backend.premium_service.service.PremiumService;
import com.exe.skillverse_backend.student_verification_service.service.StudentVerificationService;
import com.exe.skillverse_backend.user_service.service.UserProfileService;
import com.exe.skillverse_backend.wallet_service.entity.Wallet;
import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
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
        private static final DateTimeFormatter VIETNAMESE_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        private final PremiumPlanRepository premiumPlanRepository;
        private final UserSubscriptionRepository userSubscriptionRepository;
        private final UserRepository userRepository;
        private final PaymentTransactionRepository paymentTransactionRepository;
        private final WalletService walletService;
        private final SubscriptionCancellationRepository cancellationRepository;
        private final UserProfileService userProfileService;
        private final PremiumEmailService premiumEmailService;
        private final NotificationServiceImpl notificationService;
        private final StudentVerificationService studentVerificationService;
        private final ObjectMapper objectMapper;

        private static final List<String> STUDENT_EMAIL_DOMAINS = List.of(
                        ".edu", ".edu.vn", ".ac.uk", "university.", "student.", ".edu.au");
        private static final Map<PremiumPlan.PlanType, Integer> LEARNER_PLAN_ORDER = Map.of(
                        PremiumPlan.PlanType.FREE_TIER, 0,
                        PremiumPlan.PlanType.STUDENT_PACK, 1,
                        PremiumPlan.PlanType.PREMIUM_BASIC, 2,
                        PremiumPlan.PlanType.PREMIUM_PLUS, 3);

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
        public List<PremiumPlanResponse> getAvailablePlansForUser(Long userId, boolean includeFreeTier) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
                PremiumPlan.TargetRole targetRole = resolveTargetRoleByPrimaryRole(user.getPrimaryRole());
                return getAvailablePlansByTargetRole(targetRole, includeFreeTier);
        }

        @Override
        @Transactional(readOnly = true)
        public List<PremiumPlanResponse> getAvailablePlansForGuest(boolean includeFreeTier) {
                return getAvailablePlansByTargetRole(PremiumPlan.TargetRole.LEARNER, includeFreeTier);
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
        public Optional<PremiumPlanResponse> getPlanByIdForUser(Long userId, Long planId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
                PremiumPlan.TargetRole targetRole = resolveTargetRoleByPrimaryRole(user.getPrimaryRole());
                return premiumPlanRepository.findById(planId)
                                .filter(plan -> Boolean.TRUE.equals(plan.getIsActive()))
                                .filter(plan -> isPlanVisibleForRole(plan, targetRole, true))
                                .map(this::convertToPremiumPlanResponse);
        }

        @Override
        @Transactional(readOnly = true)
        public Optional<PremiumPlanResponse> getPlanByIdForGuest(Long planId) {
                return premiumPlanRepository.findById(planId)
                                .filter(plan -> Boolean.TRUE.equals(plan.getIsActive()))
                                .filter(plan -> isPlanVisibleForRole(plan, PremiumPlan.TargetRole.LEARNER, true))
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
        @Transactional(readOnly = true)
        public SubscriptionCheckoutPreviewResponse getCheckoutPreview(
                        Long buyerUserId,
                        Long planId,
                        boolean applyStudentDiscount) {
                return convertToCheckoutPreviewResponse(
                                buildCheckoutPreview(buyerUserId, planId, applyStudentDiscount));
        }

        @Override
        @Transactional
        public Optional<UserSubscriptionResponse> getCurrentSubscription(Long userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));
                reconcileCurrentSubscriptionState(user, "request:current-subscription");
                Optional<UserSubscription> activeSub = userSubscriptionRepository.findCurrentActiveSubscription(user);
                if (activeSub.isPresent()) {
                        UserSubscription scheduledDowngrade = userSubscriptionRepository
                                        .findPendingScheduledDowngrades(user)
                                        .stream()
                                        .findFirst()
                                        .orElse(null);
                        return activeSub.map(subscription -> convertToUserSubscriptionResponse(subscription, scheduledDowngrade));
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
                assignFreeTierIfMissing(userId);
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
                        throw new RuntimeException("Payment user is not allowed for this subscription");
                }

                LocalDateTime activationTime = LocalDateTime.now();
                PremiumPlan plan = subscription.getPlan();
                UserSubscription upgradeSource = resolveUpgradeSourceSubscription(subscription, paymentTransaction);
                LocalDateTime targetEndDate = null;
                Boolean inheritedAutoRenew = null;

                if (upgradeSource != null) {
                        LocalDateTime previousRenewalDate = upgradeSource.getEndDate();
                        inheritedAutoRenew = upgradeSource.getAutoRenew();
                        upgradeSource.cancel("Upgraded to " + plan.getDisplayName());
                        upgradeSource.setEndDate(activationTime);
                        userSubscriptionRepository.save(upgradeSource);
                        if (shouldResetUpgradeCycle(paymentTransaction, subscription.getUser(), upgradeSource.getPlan(), plan)) {
                                targetEndDate = calculateEndDate(activationTime, plan.getDurationMonths());
                        } else {
                                targetEndDate = previousRenewalDate != null && previousRenewalDate.isAfter(activationTime)
                                                ? previousRenewalDate
                                                : calculateEndDate(activationTime, plan.getDurationMonths());
                        }
                } else {
                        targetEndDate = calculateEndDate(activationTime, plan.getDurationMonths());
                }

                subscription.setStartDate(activationTime);
                subscription.setEndDate(targetEndDate);
                subscription.setIsActive(true);
                subscription.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
                subscription.setPaymentTransaction(paymentTransaction);
                if (inheritedAutoRenew != null) {
                        subscription.setAutoRenew(inheritedAutoRenew);
                }
                captureCurrentCyclePaidAmount(subscription, paymentTransaction.getAmount());
                ensureRenewalSnapshot(subscription);

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
        @Transactional
        public boolean hasActivePremiumSubscription(Long userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));
                reconcileCurrentSubscriptionState(user, "request:premium-status");
                // Premium means active non-free subscription
                return userSubscriptionRepository.hasActiveNonFreeSubscription(user, LocalDateTime.now());
        }

        @Override
        @Deprecated
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
                LocalDateTime start = now.plusDays(PremiumConstants.EXPIRY_NOTIFICATION_DAYS)
                                .withHour(0).withMinute(0).withSecond(0);
                LocalDateTime end = now.plusDays(PremiumConstants.EXPIRY_NOTIFICATION_DAYS)
                                .withHour(23).withMinute(59).withSecond(59);

                List<UserSubscription> expiring = userSubscriptionRepository.findSubscriptionsExpiringSoon(start, end);

                for (UserSubscription sub : expiring) {
                        BigDecimal renewalAmount = resolveRenewalAmount(sub);
                        if (Boolean.TRUE.equals(sub.getAutoRenew())) {
                                boolean hasEnoughForRenewal = walletService.hasAvailableCash(
                                                sub.getUser().getId(),
                                                renewalAmount);
                                if (!hasEnoughForRenewal) {
                                        notificationService.createNotification(
                                                        sub.getUser().getId(),
                                                        "Số dư ví chưa đủ để gia hạn tự động",
                                                        "Gói Premium của bạn sẽ hết hạn vào ngày "
                                                                        + sub.getEndDate().toLocalDate()
                                                                        + ". Hiện số dư ví chưa đủ cho khoản gia hạn "
                                                                        + renewalAmount.toPlainString()
                                                                        + " VND. Vui lòng nạp thêm để gia hạn tự động không bị gián đoạn.",
                                                        NotificationType.WARNING,
                                                        String.valueOf(sub.getId()));
                                }
                                continue;
                        }

                        String message = "Gói Premium của bạn sẽ hết hạn vào ngày "
                                        + sub.getEndDate().toLocalDate()
                                        + ". Hãy gia hạn để không bị gián đoạn.";
                        notificationService.createNotification(
                                        sub.getUser().getId(),
                                        "Gói Premium sắp hết hạn",
                                        message,
                                        NotificationType.PREMIUM_EXPIRATION,
                                        String.valueOf(sub.getId()));
                }
        }

        @Override
        @Scheduled(cron = "0 * * * * ?") // Run every minute
        @Transactional
        public void processAutoRenewals() {
                log.info("🔄 Starting auto-renewal process...");

                LocalDateTime now = LocalDateTime.now();

                List<UserSubscription> subscriptionsToRenew = userSubscriptionRepository
                                .findSubscriptionsForAutoRenewalForUpdate(now, UserSubscription.SubscriptionStatus.ACTIVE);

                log.info("Found {} subscriptions eligible for auto-renewal", subscriptionsToRenew.size());

                int successCount = 0;
                int failCount = 0;

                for (UserSubscription subscription : subscriptionsToRenew) {
                        try {
                                processAutoRenewal(subscription, "cron:auto-renewal");
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
                processAutoRenewal(subscription, "internal:auto-renewal");
        }

        private void processAutoRenewal(UserSubscription subscription, String trigger) {
                log.info("Processing auto-renewal for subscription {} (user: {}) via {}",
                                subscription.getId(),
                                subscription.getUser().getId(),
                                trigger);

                User user = subscription.getUser();
                PremiumPlan currentPlan = subscription.getPlan();

                if (Boolean.TRUE.equals(userSubscriptionRepository.hasPendingScheduledDowngrade(user))) {
                        subscription.setAutoRenew(false);
                        userSubscriptionRepository.save(subscription);
                        log.info("Skipping auto-renewal for subscription {} via {} because a scheduled downgrade is pending",
                                        subscription.getId(),
                                        trigger);
                        return;
                }

                BigDecimal price = resolveRenewalAmount(subscription);

                if (!walletService.hasAvailableCash(user.getId(), price)) {
                        handleAutoRenewalInsufficientBalance(
                                        subscription,
                                        price,
                                        "Wallet balance is not enough for auto-renewal");
                        return;
                }

                // Try to deduct from wallet
                try {
                        walletService.deductCash(user.getId(), price,
                                        "Gia hạn tự động gói " + currentPlan.getDisplayName(),
                                        WalletTransaction.TransactionType.PURCHASE_PREMIUM,
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
                        relockRenewalSnapshot(subscription, LocalDateTime.now());
                        captureCurrentCyclePaidAmount(subscription, price);
                        // Keep autoRenew = true for next cycle

                        userSubscriptionRepository.save(subscription);

                        log.info("✅ Auto-renewed subscription {} until {} via {}",
                                        subscription.getId(),
                                        newEndDate,
                                        trigger);

                        notificationService.createNotification(
                                        user.getId(),
                                        "Gia hạn Premium thành công",
                                        "Gói " + currentPlan.getDisplayName() + " của bạn đã được gia hạn thành công với số tiền "
                                                        + price.toPlainString()
                                                        + " VND. Kỳ hiện tại có hiệu lực đến ngày "
                                                        + newEndDate.toLocalDate() + ".",
                                        NotificationType.PREMIUM_PURCHASE,
                                        String.valueOf(subscription.getId()));

                        // Send auto-renewal success email
                        premiumEmailService.sendAutoRenewalSuccessEmail(user, subscription, price);

                } catch (RuntimeException e) {
                        if (isInsufficientBalanceError(e)) {
                                handleAutoRenewalInsufficientBalance(subscription, price, e.getMessage());
                                return;
                        }

                        log.error("❌ Auto-renewal encountered an operational error for subscription {} via {}. Keeping auto-renewal enabled. Error: {}",
                                        subscription.getId(),
                                        trigger,
                                        e.getMessage(),
                                        e);
                        throw e;
                }
        }

        private void handleAutoRenewalInsufficientBalance(
                        UserSubscription subscription,
                        BigDecimal renewalAmount,
                        String reason) {
                User user = subscription.getUser();
                PremiumPlan plan = subscription.getPlan();

                log.warn("⚠️ Auto-renewal failed for subscription {} due to insufficient wallet balance. userId={}, amount={}, reason={}",
                                subscription.getId(),
                                user.getId(),
                                renewalAmount,
                                reason);

                subscription.setAutoRenew(false);
                userSubscriptionRepository.save(subscription);

                notificationService.createNotification(
                                user.getId(),
                                "Gia hạn tự động thất bại",
                                "Không thể gia hạn tự động gói " + plan.getDisplayName()
                                                + " vì ví không đủ số dư cho khoản "
                                                + renewalAmount.toPlainString()
                                                + " VND. Hãy nạp thêm tiền trước ngày "
                                                + subscription.getEndDate().toLocalDate()
                                                + " nếu bạn muốn tiếp tục sử dụng gói này.",
                                NotificationType.WARNING,
                                String.valueOf(subscription.getId()));

                premiumEmailService.sendAutoRenewalFailedEmail(user, subscription, renewalAmount);
        }

        private boolean isInsufficientBalanceError(RuntimeException error) {
                if (error.getMessage() == null) {
                        return false;
                }

                String normalized = error.getMessage().toLowerCase();
                return normalized.contains("số dư")
                                || normalized.contains("khả dụng không đủ")
                                || normalized.contains("insufficient");
        }

        private void reconcileCurrentSubscriptionState(User user, String trigger) {
                if (user == null) {
                        return;
                }

                LocalDateTime now = LocalDateTime.now();

                List<UserSubscription> dueScheduled = userSubscriptionRepository
                                .findDueScheduledDowngradesForUserForUpdate(user, now);
                if (!dueScheduled.isEmpty()) {
                        activateScheduledDowngrade(dueScheduled.get(0), now, trigger);
                        return;
                }

                List<UserSubscription> dueAutoRenew = userSubscriptionRepository
                                .findDueAutoRenewSubscriptionsForUserForUpdate(user, now);
                if (!dueAutoRenew.isEmpty()) {
                        log.info("Reconciling due auto-renewal for user {} via {}", user.getId(), trigger);
                        processAutoRenewal(dueAutoRenew.get(0), trigger);
                        return;
                }

                List<UserSubscription> expiredActive = userSubscriptionRepository
                                .findExpiredActiveSubscriptionsForUserForUpdate(user, now);
                if (!expiredActive.isEmpty()) {
                        log.info("Reconciling expired subscription state for user {} via {}", user.getId(), trigger);
                        for (UserSubscription subscription : expiredActive) {
                                subscription.expire();
                                userSubscriptionRepository.save(subscription);
                        }
                        assignFreeTierIfMissing(user.getId());
                }
        }

        private static final int BATCH_SIZE = 500;

        @Scheduled(cron = "0 * * * * ?")
        @Transactional
        public void processScheduledDowngradeActivations() {
                LocalDateTime now = LocalDateTime.now();
                int activatedScheduledDowngrades = activateDueScheduledDowngrades(now);
                if (activatedScheduledDowngrades == 0) {
                        log.debug("No scheduled downgrades activated at {}", now);
                }
        }

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
                        log.debug("No subscriptions expired this hour");
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

        private int activateDueScheduledDowngrades(LocalDateTime now) {
                List<UserSubscription> dueScheduled = userSubscriptionRepository.findDueScheduledDowngradesForUpdate(now);
                if (dueScheduled.isEmpty()) {
                        return 0;
                }

                int activatedCount = 0;
                for (UserSubscription scheduled : dueScheduled) {
                        if (activateScheduledDowngrade(scheduled, now, "cron:scheduled-downgrade")) {
                                activatedCount++;
                        }
                }

                if (activatedCount > 0) {
                        log.info("Activated {} scheduled downgrade subscriptions", activatedCount);
                }
                return activatedCount;
        }

        private boolean activateScheduledDowngrade(
                        UserSubscription scheduled,
                        LocalDateTime now,
                        String trigger) {
                User recipient = scheduled.getUser();

                Boolean hasActiveNonFree = userSubscriptionRepository.hasActiveNonFreeSubscription(recipient, now);
                if (Boolean.TRUE.equals(hasActiveNonFree)) {
                        return false;
                }

                if (!Boolean.TRUE.equals(scheduled.getPlan().getIsActive())) {
                        log.warn("Skipping scheduled downgrade activation for subscription {} because target plan is inactive",
                                        scheduled.getId());
                        return false;
                }

                scheduled.setIsActive(true);
                scheduled.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
                scheduled.setCancellationReason(null);
                if (scheduled.getCurrentCyclePaidAmountSnapshot() == null) {
                        captureCurrentCyclePaidAmount(scheduled, BigDecimal.ZERO);
                }
                ensureRenewalSnapshot(scheduled);
                userSubscriptionRepository.save(scheduled);

                log.info("Activated scheduled downgrade {} for user {} via {}",
                                scheduled.getId(), recipient.getId(), trigger);

                notificationService.createNotification(
                                recipient.getId(),
                                "Gói Premium mới đã có hiệu lực",
                                "Gói " + scheduled.getPlan().getDisplayName()
                                                + " đã được kích hoạt theo lịch chuyển gói.",
                                NotificationType.PREMIUM_PURCHASE,
                                String.valueOf(scheduled.getId()));
                return true;
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
                        .discountedPricing(false)
                        .isStudentSubscription(false)
                        .autoRenew(false)
                        .build();
                captureCurrentCyclePaidAmount(freeSub, BigDecimal.ZERO);
                
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
                                freeSub.setDiscountedPricing(false);
                                freeSub.setIsStudentSubscription(false);
                                captureCurrentCyclePaidAmount(freeSub, BigDecimal.ZERO);
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
                                .discountedPricing(false)
                                .isStudentSubscription(false)
                                .autoRenew(false)
                                .build();
                captureCurrentCyclePaidAmount(freeSub, BigDecimal.ZERO);
                
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
                reconcileCurrentSubscriptionState(user, "request:ensure-active-or-free");

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
                                .discountPercent(plan.getDiscountPercent())
                                .discountedPrice(plan.getDiscountedPrice())
                                .studentPrice(plan.getDiscountedPrice())
                                .studentDiscountPercent(plan.getDiscountPercent())
                                .features(parsePlanFeatures(plan.getFeatures()))
                                .isActive(plan.getIsActive())
                                .availableForSubscription(true)
                                .build();
        }

        private SubscriptionCheckoutPreviewResponse convertToCheckoutPreviewResponse(
                        CheckoutPreviewDetails checkoutPreview) {
                return SubscriptionCheckoutPreviewResponse.builder()
                                .eligible(checkoutPreview.eligible())
                                .upgrade(checkoutPreview.upgrade())
                                .samePlan(checkoutPreview.samePlan())
                                .downgrade(checkoutPreview.downgrade())
                                .buyerUserId(checkoutPreview.buyer().getId())
                                .targetUserId(checkoutPreview.recipient().getId())
                                .currentSubscriptionId(checkoutPreview.currentActiveSubscription()
                                                .map(UserSubscription::getId)
                                                .orElse(null))
                                .currentPlan(checkoutPreview.currentActiveSubscription()
                                                .map(UserSubscription::getPlan)
                                                .map(this::convertToPremiumPlanResponse)
                                                .orElse(null))
                                .targetPlan(convertToPremiumPlanResponse(checkoutPreview.targetPlan()))
                                .fullPrice(scaleCurrency(checkoutPreview.targetPlan().getPrice()))
                                .effectivePrice(checkoutPreview.effectivePrice())
                                .amountDue(checkoutPreview.amountDue())
                                .currentPlanCredit(checkoutPreview.currentPlanCredit())
                                .proratedTargetPrice(checkoutPreview.proratedTargetPrice())
                                .remainingDays(checkoutPreview.remainingDays())
                                .nextRenewalDate(checkoutPreview.nextRenewalDate())
                                .currency(checkoutPreview.targetPlan().getCurrency())
                                .pricingMode(checkoutPreview.pricingMode())
                                .message(checkoutPreview.message())
                                .build();
        }

        private CheckoutPreviewDetails buildCheckoutPreview(
                        Long buyerUserId,
                        Long planId,
                        boolean legacyApplyStudentDiscount) {
                if (legacyApplyStudentDiscount) {
                        log.debug(
                                        "Ignoring legacy applyStudentDiscount=true for buyer {} and plan {} because pricing is resolved by backend policy.",
                                        buyerUserId,
                                        planId);
                }
                User buyer = userRepository.findById(buyerUserId)
                                .orElseThrow(() -> new RuntimeException("Buyer not found with ID: " + buyerUserId));
                User recipient = buyer;
                PremiumPlan targetPlan = premiumPlanRepository.findById(planId)
                                .filter(PremiumPlan::getIsActive)
                                .orElseThrow(() -> new RuntimeException("Premium plan not found: " + planId));

                validatePlanEligibility(recipient, targetPlan);

                Optional<UserSubscription> currentActiveSubscription = userSubscriptionRepository
                                .findCurrentActiveSubscription(recipient)
                                .filter(subscription -> Boolean.TRUE.equals(subscription.getIsActive()))
                                .filter(subscription -> subscription.getStatus() == UserSubscription.SubscriptionStatus.ACTIVE);

                boolean discountApplied = isConfiguredDiscountEligible(targetPlan, recipient);
                BigDecimal effectivePrice = resolveEffectivePlanPrice(targetPlan, recipient);

                if (currentActiveSubscription.isEmpty()
                                || currentActiveSubscription.get().getPlan().getPlanType() == PremiumPlan.PlanType.FREE_TIER) {
                        return new CheckoutPreviewDetails(
                                        true,
                                        false,
                                        false,
                                        false,
                                        buyer,
                                        recipient,
                                        targetPlan,
                                        currentActiveSubscription,
                                        discountApplied,
                                        effectivePrice,
                                        effectivePrice,
                                        BigDecimal.ZERO,
                                        BigDecimal.ZERO,
                                        0L,
                                        null,
                                        SubscriptionCheckoutPreviewResponse.PricingMode.FULL_PURCHASE,
                                        "Thanh toán toàn bộ gói Premium.");
                }

                UserSubscription currentSubscription = currentActiveSubscription.get();
                PremiumPlan currentPlan = currentSubscription.getPlan();
                BigDecimal currentEffectivePrice = resolvePlanCyclePrice(
                                currentPlan,
                                resolveDiscountApplied(currentSubscription));

                if (currentPlan.getId().equals(targetPlan.getId())) {
                        return new CheckoutPreviewDetails(
                                        false,
                                        false,
                                        true,
                                        false,
                                        buyer,
                                        recipient,
                                        targetPlan,
                                        currentActiveSubscription,
                                        discountApplied,
                                        effectivePrice,
                                        BigDecimal.ZERO,
                                        BigDecimal.ZERO,
                                        BigDecimal.ZERO,
                                        currentSubscription.getDaysRemaining(),
                                        currentSubscription.getEndDate(),
                                        SubscriptionCheckoutPreviewResponse.PricingMode.CURRENT_PLAN,
                                        "Bạn đang ở đúng gói này rồi.");
                }

                int planDirection = comparePlanProgression(currentPlan, targetPlan);
                if (planDirection <= 0) {
                        if (planDirection < 0) {
                                return new CheckoutPreviewDetails(
                                                true,
                                                false,
                                                false,
                                                true,
                                                buyer,
                                                recipient,
                                                targetPlan,
                                                currentActiveSubscription,
                                                discountApplied,
                                                effectivePrice,
                                                BigDecimal.ZERO,
                                                BigDecimal.ZERO,
                                                effectivePrice,
                                                currentSubscription.getDaysRemaining(),
                                                currentSubscription.getEndDate(),
                                                SubscriptionCheckoutPreviewResponse.PricingMode.DOWNGRADE_SCHEDULED,
                                                "Gói thấp hơn sẽ được đặt lịch và chỉ có hiệu lực khi gói hiện tại kết thúc.");
                        }

                        return new CheckoutPreviewDetails(
                                        false,
                                        false,
                                        false,
                                        true,
                                        buyer,
                                        recipient,
                                        targetPlan,
                                        currentActiveSubscription,
                                        discountApplied,
                                        effectivePrice,
                                        BigDecimal.ZERO,
                                        BigDecimal.ZERO,
                                        BigDecimal.ZERO,
                                        currentSubscription.getDaysRemaining(),
                                        currentSubscription.getEndDate(),
                                        SubscriptionCheckoutPreviewResponse.PricingMode.DOWNGRADE_NOT_ALLOWED,
                                        "Hiện chỉ hỗ trợ nâng cấp lên gói có giá trị cao hơn.");
                }

                LocalDateTime now = LocalDateTime.now();
                long remainingDays = calculateRemainingDays(now, currentSubscription.getEndDate());
                if (!supportsImmediateLearnerUpgrade(recipient, currentPlan, targetPlan)) {
                        return new CheckoutPreviewDetails(
                                        false,
                                        true,
                                        false,
                                        false,
                                        buyer,
                                        recipient,
                                        targetPlan,
                                        currentActiveSubscription,
                                        discountApplied,
                                        effectivePrice,
                                        BigDecimal.ZERO,
                                        BigDecimal.ZERO,
                                        effectivePrice,
                                        remainingDays,
                                        currentSubscription.getEndDate(),
                                        SubscriptionCheckoutPreviewResponse.PricingMode.UPGRADE_NOT_ALLOWED,
                                        "Tài khoản này không hỗ trợ nâng cấp trực tiếp.");
                }

                if (!isWithinUpgradeGraceWindow(currentSubscription)) {
                        LocalDateTime nextRenewalDate = calculateEndDate(now, targetPlan.getDurationMonths());
                        return new CheckoutPreviewDetails(
                                        true,
                                        true,
                                        false,
                                        false,
                                        buyer,
                                        recipient,
                                        targetPlan,
                                        currentActiveSubscription,
                                        discountApplied,
                                        effectivePrice,
                                        effectivePrice,
                                        BigDecimal.ZERO,
                                        effectivePrice,
                                        remainingDays,
                                        nextRenewalDate,
                                        SubscriptionCheckoutPreviewResponse.PricingMode.UPGRADE_FULL_PRICE,
                                        "Ưu đãi 72 giờ đã hết. Gói mới sẽ tính giá đầy đủ và bắt đầu lại từ hôm nay.");
                }

                BigDecimal currentPlanCredit = resolveCurrentCyclePaidAmount(currentSubscription, currentEffectivePrice);
                BigDecimal amountDue = scaleCurrency(effectivePrice.subtract(currentPlanCredit).max(BigDecimal.ZERO));
                LocalDateTime nextRenewalDate = calculateEndDate(now, targetPlan.getDurationMonths());

                return new CheckoutPreviewDetails(
                                true,
                                true,
                                false,
                                false,
                                buyer,
                                recipient,
                                targetPlan,
                                currentActiveSubscription,
                                discountApplied,
                                effectivePrice,
                                amountDue,
                                currentPlanCredit,
                                effectivePrice,
                                remainingDays,
                                nextRenewalDate,
                                SubscriptionCheckoutPreviewResponse.PricingMode.UPGRADE_GRACE_WINDOW,
                                "Thanh toán phần chênh lệch trong 72 giờ đầu để reset toàn bộ thời hạn và quyền lợi của gói mới.");
        }


        private UserSubscription resolveUpgradeSourceSubscription(
                        UserSubscription newSubscription,
                        PaymentTransaction paymentTransaction) {
                Optional<Long> sourceSubscriptionId = extractLongFieldFromMetadata(
                                paymentTransaction.getMetadata(), "currentSubscriptionId");
                if (sourceSubscriptionId.isPresent()) {
                        return userSubscriptionRepository.findById(sourceSubscriptionId.get())
                                        .filter(existing -> existing.getUser().getId().equals(newSubscription.getUser().getId()))
                                        .filter(existing -> Boolean.TRUE.equals(existing.getIsActive()))
                                        .filter(existing -> existing.getStatus() == UserSubscription.SubscriptionStatus.ACTIVE)
                                        .filter(existing -> existing.getPlan().getPlanType() != PremiumPlan.PlanType.FREE_TIER)
                                        .orElse(null);
                }

                return userSubscriptionRepository.findCurrentActiveSubscription(newSubscription.getUser())
                                .filter(existing -> !existing.getId().equals(newSubscription.getId()))
                                .filter(existing -> existing.getPlan().getPlanType() != PremiumPlan.PlanType.FREE_TIER)
                                .filter(existing -> comparePlanProgression(existing.getPlan(), newSubscription.getPlan()) > 0)
                                .orElse(null);
        }

        private int comparePlanProgression(PremiumPlan currentPlan, PremiumPlan targetPlan) {
                if (currentPlan == null || targetPlan == null) {
                        return 0;
                }

                int currentOrder = resolvePlanOrder(currentPlan);
                int targetOrder = resolvePlanOrder(targetPlan);

                if (currentOrder != targetOrder) {
                        return Integer.compare(targetOrder, currentOrder);
                }

                BigDecimal currentPrice = scaleCurrency(currentPlan.getPrice());
                BigDecimal targetPrice = scaleCurrency(targetPlan.getPrice());
                return targetPrice.compareTo(currentPrice);
        }

        private int resolvePlanOrder(PremiumPlan plan) {
                if (plan == null || plan.getPlanType() == null) {
                        return 0;
                }

                PremiumPlan.TargetRole targetRole = resolvePlanTargetRole(plan);
                if (targetRole == PremiumPlan.TargetRole.LEARNER) {
                        return LEARNER_PLAN_ORDER.getOrDefault(plan.getPlanType(), 0);
                }

                if (targetRole == PremiumPlan.TargetRole.RECRUITER) {
                        return 100;
                }

                return LEARNER_PLAN_ORDER.getOrDefault(plan.getPlanType(), 0);
        }

        private boolean supportsImmediateLearnerUpgrade(
                        User recipient,
                        PremiumPlan currentPlan,
                        PremiumPlan targetPlan) {
                if (recipient == null || currentPlan == null || targetPlan == null) {
                        return false;
                }

                if (recipient.getPrimaryRole() != PrimaryRole.USER) {
                        return false;
                }

                if (resolvePlanTargetRole(currentPlan) != PremiumPlan.TargetRole.LEARNER
                                || resolvePlanTargetRole(targetPlan) != PremiumPlan.TargetRole.LEARNER) {
                        return false;
                }

                return true;
        }

        private boolean shouldResetUpgradeCycle(
                        PaymentTransaction paymentTransaction,
                        User recipient,
                        PremiumPlan currentPlan,
                        PremiumPlan targetPlan) {
                Optional<String> pricingMode = extractTextFieldFromMetadata(paymentTransaction.getMetadata(), "pricingMode");
                if (pricingMode.isPresent()) {
                        return pricingMode.get().equals(SubscriptionCheckoutPreviewResponse.PricingMode.UPGRADE_GRACE_WINDOW.name())
                                        || pricingMode.get().equals(SubscriptionCheckoutPreviewResponse.PricingMode.UPGRADE_FULL_PRICE.name());
                }

                return supportsImmediateLearnerUpgrade(recipient, currentPlan, targetPlan)
                                && paymentTransaction.getType() == PaymentTransaction.PaymentType.PREMIUM_SUBSCRIPTION;
        }

        private boolean isWithinUpgradeGraceWindow(UserSubscription subscription) {
                if (subscription == null || subscription.getStartDate() == null) {
                        return false;
                }

                long hoursSinceStart = Duration.between(subscription.getStartDate(), LocalDateTime.now()).toHours();
                return hoursSinceStart >= 0 && hoursSinceStart <= PremiumConstants.UPGRADE_GRACE_WINDOW_HOURS;
        }

        private BigDecimal resolveCurrentCyclePaidAmount(UserSubscription subscription, BigDecimal fallbackAmount) {
                if (subscription != null && subscription.getCurrentCyclePaidAmountSnapshot() != null) {
                        return scaleCurrency(subscription.getCurrentCyclePaidAmountSnapshot());
                }
                if (subscription != null && subscription.getPaymentTransaction() != null
                                && subscription.getPaymentTransaction().getAmount() != null
                                && subscription.getPaymentTransaction()
                                                .getStatus() == PaymentTransaction.PaymentStatus.COMPLETED) {
                        return scaleCurrency(subscription.getPaymentTransaction().getAmount());
                }
                return scaleCurrency(fallbackAmount);
        }

        private BigDecimal resolveRefundBaseAmount(UserSubscription subscription) {
                if (subscription == null) {
                        return BigDecimal.ZERO;
                }
                return resolveCurrentCyclePaidAmount(
                                subscription,
                                resolvePlanCyclePrice(subscription.getPlan(), resolveDiscountApplied(subscription)));
        }

        private BigDecimal resolvePlanCyclePrice(PremiumPlan plan, Boolean discountApplied) {
                if (plan == null) {
                        return BigDecimal.ZERO;
                }

                BigDecimal effectivePrice = Boolean.TRUE.equals(discountApplied)
                                ? plan.getDiscountedPrice()
                                : plan.getPrice();
                return scaleCurrency(effectivePrice);
        }

        private boolean isConfiguredDiscountEligible(PremiumPlan plan, User recipient) {
                if (plan == null || recipient == null) {
                        return false;
                }

                if (plan.getDiscountPercent() == null || plan.getDiscountPercent().compareTo(BigDecimal.ZERO) <= 0) {
                        return false;
                }

                PremiumPlan.TargetRole recipientRole = resolveTargetRoleByPrimaryRole(recipient.getPrimaryRole());
                return isPlanPurchasableForRole(plan, recipientRole);
        }

        private BigDecimal resolveEffectivePlanPrice(PremiumPlan plan, User recipient) {
                if (plan == null) {
                        return BigDecimal.ZERO;
                }
                return resolvePlanCyclePrice(plan, isConfiguredDiscountEligible(plan, recipient));
        }

        private BigDecimal resolveRenewalAmount(UserSubscription subscription) {
                if (subscription == null) {
                        return BigDecimal.ZERO;
                }
                if (subscription.getRenewalPriceSnapshot() != null) {
                        return scaleCurrency(subscription.getRenewalPriceSnapshot());
                }
                return resolvePlanCyclePrice(subscription.getPlan(), resolveDiscountApplied(subscription));
        }

        private LocalDateTime resolveRenewalAttemptDate(UserSubscription subscription) {
                if (subscription == null || subscription.getEndDate() == null) {
                        return null;
                }
                LocalDateTime endDate = subscription.getEndDate();
                int intervalMinutes = PremiumConstants.AUTO_RENEWAL_INTERVAL_MINUTES;

                if (intervalMinutes <= 0) {
                        return endDate;
                }

                if (endDate.getSecond() == 0 && endDate.getNano() == 0
                                && endDate.getMinute() % intervalMinutes == 0) {
                        return endDate;
                }

                LocalDateTime normalizedEndDate = endDate.truncatedTo(ChronoUnit.MINUTES);
                int minuteRemainder = normalizedEndDate.getMinute() % intervalMinutes;
                int minutesUntilNextRun = minuteRemainder == 0
                                ? intervalMinutes
                                : intervalMinutes - minuteRemainder;

                return normalizedEndDate.plusMinutes(minutesUntilNextRun);
        }

        private void ensureRenewalSnapshot(UserSubscription subscription) {
                if (subscription == null) {
                        return;
                }

                if (subscription.getRenewalPriceSnapshot() == null || subscription.getRenewalPriceLockedAt() == null) {
                        relockRenewalSnapshot(subscription, LocalDateTime.now());
                }
        }

        private void relockRenewalSnapshot(UserSubscription subscription, LocalDateTime lockedAt) {
                if (subscription == null) {
                        return;
                }

                subscription.setRenewalPriceSnapshot(
                                resolvePlanCyclePrice(subscription.getPlan(), resolveDiscountApplied(subscription)));
                subscription.setRenewalPriceLockedAt(lockedAt != null ? lockedAt : LocalDateTime.now());
        }

        private void captureCurrentCyclePaidAmount(UserSubscription subscription, BigDecimal amount) {
                if (subscription == null) {
                        return;
                }

                BigDecimal normalizedAmount = amount == null ? BigDecimal.ZERO : amount;
                if (normalizedAmount.compareTo(BigDecimal.ZERO) < 0) {
                        normalizedAmount = BigDecimal.ZERO;
                }

                subscription.setCurrentCyclePaidAmountSnapshot(scaleCurrency(normalizedAmount));
        }

        private boolean resolveDiscountApplied(UserSubscription subscription) {
                return subscription != null && subscription.isDiscountedPricingApplied();
        }

        private BigDecimal calculateProratedAmount(
                        BigDecimal fullPrice,
                        LocalDateTime billingStart,
                        LocalDateTime billingEnd,
                        LocalDateTime usageStart,
                        LocalDateTime usageEnd) {
                long totalMinutes = Math.max(1L, Duration.between(billingStart, billingEnd).toMinutes());
                long usageMinutes = Math.max(0L, Duration.between(usageStart, usageEnd).toMinutes());
                if (usageMinutes == 0L) {
                        return BigDecimal.ZERO;
                }

                return fullPrice
                                .multiply(BigDecimal.valueOf(usageMinutes))
                                .divide(BigDecimal.valueOf(totalMinutes), 0, RoundingMode.HALF_UP);
        }

        private long calculateRemainingDays(LocalDateTime now, LocalDateTime endDate) {
                long remainingMinutes = Math.max(0L, Duration.between(now, endDate).toMinutes());
                if (remainingMinutes == 0L) {
                        return 0L;
                }
                return Math.max(1L, (remainingMinutes + 1_439L) / 1_440L);
        }

        private BigDecimal scaleCurrency(BigDecimal value) {
                if (value == null) {
                        return BigDecimal.ZERO;
                }
                return value.setScale(0, RoundingMode.HALF_UP);
        }

        private record CheckoutPreviewDetails(
                        boolean eligible,
                        boolean upgrade,
                        boolean samePlan,
                        boolean downgrade,
                        User buyer,
                        User recipient,
                        PremiumPlan targetPlan,
                        Optional<UserSubscription> currentActiveSubscription,
                        boolean discountApplied,
                        BigDecimal effectivePrice,
                        BigDecimal amountDue,
                        BigDecimal currentPlanCredit,
                        BigDecimal proratedTargetPrice,
                        long remainingDays,
                        LocalDateTime nextRenewalDate,
                        SubscriptionCheckoutPreviewResponse.PricingMode pricingMode,
                        String message) {
        }

        private UserSubscriptionResponse convertToUserSubscriptionResponse(UserSubscription subscription) {
                return convertToUserSubscriptionResponse(subscription, null);
        }

        private UserSubscriptionResponse convertToUserSubscriptionResponse(
                        UserSubscription subscription,
                        UserSubscription scheduledDowngrade) {
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
                                .isStudentSubscription(resolveDiscountApplied(subscription))
                                .isDiscountedSubscription(resolveDiscountApplied(subscription))
                                .autoRenew(subscription.getAutoRenew())
                                .renewalPrice(resolveRenewalAmount(subscription))
                                .renewalAttemptDate(resolveRenewalAttemptDate(subscription))
                                .renewalPriceLockedAt(subscription.getRenewalPriceLockedAt())
                                .scheduledChangePlan(
                                                scheduledDowngrade != null
                                                                ? convertToPremiumPlanResponse(scheduledDowngrade.getPlan())
                                                                : null)
                                .scheduledChangeEffectiveDate(
                                                scheduledDowngrade != null ? scheduledDowngrade.getStartDate() : null)
                                .scheduledChangeAutoRenew(
                                                scheduledDowngrade != null ? scheduledDowngrade.getAutoRenew() : null)
                                .scheduledChangeRenewalPrice(
                                                scheduledDowngrade != null ? resolveRenewalAmount(scheduledDowngrade) : null)
                                .scheduledChangeRenewalAttemptDate(
                                                scheduledDowngrade != null ? resolveRenewalAttemptDate(scheduledDowngrade) : null)
                                .paymentTransactionId(
                                                subscription.getPaymentTransaction() != null
                                                                ? subscription.getPaymentTransaction().getId()
                                                                : null)
                                .daysRemaining(subscription.getDaysRemaining())
                                .currentlyActive(subscription.isCurrentlyActive())
                                .cancellationReason(subscription.getCancellationReason())
                                .cancelledAt(subscription.getCancelledAt())
                                .createdAt(subscription.getCreatedAt())
                                .updatedAt(subscription.getUpdatedAt())
                                .build();
        }

        @Override
        @Transactional
        public UserSubscriptionResponse purchaseWithWalletCash(Long buyerId, Long planId, boolean applyStudentDiscount) {
                log.info("💰 User {} purchasing premium plan {} with wallet cash", buyerId, planId);
                CheckoutPreviewDetails checkoutPreview = buildCheckoutPreview(
                                buyerId, planId, applyStudentDiscount);

                if (!checkoutPreview.eligible()) {
                        throw new RuntimeException(checkoutPreview.message());
                }

                User buyer = checkoutPreview.buyer();
                User recipient = checkoutPreview.recipient();
                PremiumPlan plan = checkoutPreview.targetPlan();
                Optional<UserSubscription> existingSubscription = checkoutPreview.currentActiveSubscription();

                if (existingSubscription.isPresent() &&
                                existingSubscription.get().getPlan().getPlanType() == PremiumPlan.PlanType.FREE_TIER) {
                        UserSubscription freeTierSub = existingSubscription.get();
                        freeTierSub.suspend("Upgrading to Premium via Wallet - will reactivate when premium expires");
                        userSubscriptionRepository.save(freeTierSub);
                }

                boolean isScheduledDowngrade = checkoutPreview.pricingMode() == SubscriptionCheckoutPreviewResponse.PricingMode.DOWNGRADE_SCHEDULED;
                BigDecimal finalPrice = isScheduledDowngrade ? BigDecimal.ZERO : checkoutPreview.amountDue();

                log.info("💵 Checkout amount due: {} VND (upgrade: {}, role discount applied: {})",
                                finalPrice, checkoutPreview.upgrade(), checkoutPreview.discountApplied());

                if (finalPrice.compareTo(BigDecimal.ZERO) > 0) {
                        String purchaseDescription = String.format("Mua gói Premium: %s", plan.getDisplayName());
                        try {
                                walletService.deductCash(buyerId, finalPrice, purchaseDescription,
                                                WalletTransaction.TransactionType.PURCHASE_PREMIUM,
                                                "PREMIUM_SUBSCRIPTION", planId.toString());
                        } catch (Exception e) {
                                log.error("Failed to deduct wallet balance: {}", e.getMessage());
                                throw new RuntimeException("Số dư ví không đủ hoặc thanh toán thất bại: " + e.getMessage());
                        }

                        log.info("💳 Wallet payment processed successfully");
                }

                LocalDateTime preservedRenewalDate = null;
                Boolean inheritedAutoRenew = null;
                boolean resetCycleUpgrade = checkoutPreview.pricingMode() == SubscriptionCheckoutPreviewResponse.PricingMode.UPGRADE_GRACE_WINDOW
                                || checkoutPreview.pricingMode() == SubscriptionCheckoutPreviewResponse.PricingMode.UPGRADE_FULL_PRICE;
                if (checkoutPreview.upgrade() && existingSubscription.isPresent()) {
                        UserSubscription oldSubscription = existingSubscription.get();
                        preservedRenewalDate = oldSubscription.getEndDate();
                        inheritedAutoRenew = oldSubscription.getAutoRenew();
                        oldSubscription.cancel("Upgraded to " + plan.getDisplayName() + " via wallet");
                        oldSubscription.setEndDate(LocalDateTime.now());
                        userSubscriptionRepository.save(oldSubscription);
                }

                if (isScheduledDowngrade && existingSubscription.isPresent()) {
                        UserSubscription currentSubscription = userSubscriptionRepository
                                        .findCurrentActiveSubscriptionForUpdate(recipient)
                                        .orElseThrow(() -> new RuntimeException("Không tìm thấy gói hiện tại để lên lịch chuyển gói"));
                        LocalDateTime scheduledStartDate = currentSubscription.getEndDate();
                        LocalDateTime scheduledEndDate = calculateEndDate(scheduledStartDate, plan.getDurationMonths());

                        List<UserSubscription> pendingScheduled = userSubscriptionRepository
                                        .findPendingScheduledDowngradesForUpdate(recipient);

                        UserSubscription scheduledSubscription;
                        boolean scheduleAlreadyExists = false;
                        boolean scheduleUpdated = false;
                        boolean inheritedAutoRenewPreference = Boolean.TRUE.equals(currentSubscription.getAutoRenew());
                        if (!pendingScheduled.isEmpty()) {
                                scheduledSubscription = pendingScheduled.get(0);
                                boolean existingScheduledAutoRenew = Boolean.TRUE.equals(scheduledSubscription.getAutoRenew());
                                boolean sameTargetPlan = scheduledSubscription.getPlan() != null
                                                && scheduledSubscription.getPlan().getId().equals(plan.getId());
                                boolean sameEffectiveDate = scheduledStartDate.equals(scheduledSubscription.getStartDate());
                                if (sameTargetPlan && sameEffectiveDate) {
                                        scheduleAlreadyExists = true;
                                }
                                scheduledSubscription.setPlan(plan);
                                scheduledSubscription.setStartDate(scheduledStartDate);
                                scheduledSubscription.setEndDate(scheduledEndDate);
                                scheduledSubscription.setDiscountedPricing(checkoutPreview.discountApplied());
                                scheduledSubscription.setIsStudentSubscription(checkoutPreview.discountApplied());
                                scheduledSubscription.setAutoRenew(inheritedAutoRenewPreference || existingScheduledAutoRenew);
                                scheduledSubscription.setStatus(UserSubscription.SubscriptionStatus.PENDING);
                                scheduledSubscription.setIsActive(false);
                                scheduleUpdated = !scheduleAlreadyExists;
                        } else {
                                scheduledSubscription = UserSubscription.builder()
                                                .user(recipient)
                                                .plan(plan)
                                                .startDate(scheduledStartDate)
                                                .endDate(scheduledEndDate)
                                                .isActive(false)
                                                .status(UserSubscription.SubscriptionStatus.PENDING)
                                                .discountedPricing(checkoutPreview.discountApplied())
                                                .isStudentSubscription(checkoutPreview.discountApplied())
                                                .autoRenew(inheritedAutoRenewPreference)
                                                .build();
                        }

                        if (inheritedAutoRenewPreference) {
                                currentSubscription.setAutoRenew(false);
                                userSubscriptionRepository.save(currentSubscription);
                        }

                        scheduledSubscription.setCancellationReason("SCHEDULED_DOWNGRADE:" + currentSubscription.getId());
                        captureCurrentCyclePaidAmount(scheduledSubscription, BigDecimal.ZERO);
                        relockRenewalSnapshot(scheduledSubscription, scheduledStartDate);
                        scheduledSubscription = userSubscriptionRepository.save(scheduledSubscription);

                        if (!scheduleAlreadyExists) {
                                notificationService.createNotification(
                                                recipient.getId(),
                                                scheduleUpdated ? "Đã cập nhật lịch chuyển gói Premium"
                                                                : "Đã lên lịch chuyển gói Premium",
                                                "Gói " + plan.getDisplayName() + " sẽ tự động có hiệu lực từ ngày "
                                                                + scheduledStartDate.toLocalDate()
                                                                + " sau khi gói hiện tại kết thúc.",
                                                NotificationType.PREMIUM_PURCHASE,
                                                String.valueOf(scheduledSubscription.getId()));

                        }

                        return convertToUserSubscriptionResponse(scheduledSubscription);
                }

                LocalDateTime startDate = LocalDateTime.now();
                LocalDateTime endDate = checkoutPreview.upgrade() && !resetCycleUpgrade && preservedRenewalDate != null
                                && preservedRenewalDate.isAfter(startDate)
                                ? preservedRenewalDate
                                : calculateEndDate(startDate, plan.getDurationMonths());

                UserSubscription subscription = UserSubscription.builder()
                                .user(recipient)
                                .plan(plan)
                                .startDate(startDate)
                                .endDate(endDate)
                                .isActive(true) // Activate immediately since payment is done
                                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                                .discountedPricing(checkoutPreview.discountApplied())
                                .isStudentSubscription(checkoutPreview.discountApplied())
                                .autoRenew(Boolean.TRUE.equals(inheritedAutoRenew))
                                .build();
                captureCurrentCyclePaidAmount(subscription, finalPrice);
                relockRenewalSnapshot(subscription, startDate);

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
                        String notifyMessage = "Bạn đã đăng ký gói Premium " + plan.getDisplayName() + " thành công bằng ví.";
                        
                        notificationService.createNotification(
                                        recipient.getId(),
                                        "Đăng ký Premium thành công",
                                        notifyMessage,
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

                var subscriptionOpt = userSubscriptionRepository.findCurrentActiveSubscriptionForUpdate(user);
                if (subscriptionOpt.isEmpty()) {
                        throw new RuntimeException(PremiumConstants.MSG_NO_ACTIVE_SUBSCRIPTION);
                }

                UserSubscription subscription = subscriptionOpt.get();
                List<UserSubscription> pendingScheduled = userSubscriptionRepository.findPendingScheduledDowngradesForUpdate(user);
                UserSubscription renewalTarget = pendingScheduled.isEmpty() ? subscription : pendingScheduled.get(0);

                if (renewalTarget.getPlan().getPlanType() == PremiumPlan.PlanType.FREE_TIER) {
                        throw new RuntimeException(PremiumConstants.MSG_FREE_TIER_NO_AUTO_RENEW);
                }

                // Check if already enabled
                if (renewalTarget.getAutoRenew()) {
                        throw new RuntimeException(PremiumConstants.MSG_AUTO_RENEW_ALREADY_ENABLED);
                }

                if (!pendingScheduled.isEmpty()) {
                        subscription.setAutoRenew(false);
                        userSubscriptionRepository.save(subscription);
                }

                renewalTarget.setAutoRenew(true);
                ensureRenewalSnapshot(renewalTarget);
                userSubscriptionRepository.save(renewalTarget);

                BigDecimal renewalAmount = resolveRenewalAmount(renewalTarget);
                LocalDateTime renewalAttemptDate = resolveRenewalAttemptDate(renewalTarget);

                log.info("✅ Auto-renewal enabled for user {}. Next renewal before {}",
                                userId, renewalTarget.getEndDate());

                notificationService.createNotification(
                                userId,
                                "Bật gia hạn tự động",
                                "Bạn đã bật gia hạn tự động cho gói "
                                                + renewalTarget.getPlan().getDisplayName()
                                                + (!pendingScheduled.isEmpty() ? " sau khi chuyển gói. " : ". ")
                                                + "Hệ thống sẽ thử trừ "
                                                + renewalAmount.toPlainString()
                                                + " VND từ ví vào khoảng "
                                                + renewalAttemptDate.format(VIETNAMESE_DATE_TIME_FORMATTER)
                                                + ". Hệ thống xử lý theo chu kỳ tối đa "
                                                + PremiumConstants.AUTO_RENEWAL_INTERVAL_MINUTES
                                                + " phút sau khi gói hết hạn. Mức phí này đã được khóa cho kỳ gia hạn kế tiếp.",
                                NotificationType.PREMIUM_PURCHASE,
                                String.valueOf(renewalTarget.getId()));
        }

        @Override
        @Transactional
        public void cancelAutoRenewal(Long userId) {
                log.info("🔄 Cancelling auto-renewal for user {}", userId);

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

                UserSubscription subscription = userSubscriptionRepository
                                .findCurrentActiveSubscriptionForUpdate(user)
                                .orElseThrow(() -> new RuntimeException(
                                                PremiumConstants.MSG_NO_ACTIVE_SUBSCRIPTION));

                List<UserSubscription> pendingScheduled = userSubscriptionRepository.findPendingScheduledDowngradesForUpdate(user);
                UserSubscription renewalTarget = pendingScheduled.isEmpty() ? subscription : pendingScheduled.get(0);

                if (renewalTarget.getPlan().getPlanType() == PremiumPlan.PlanType.FREE_TIER) {
                        throw new RuntimeException(PremiumConstants.MSG_FREE_TIER_NO_AUTO_RENEW);
                }

                subscription.setAutoRenew(false);
                userSubscriptionRepository.save(subscription);

                renewalTarget.setAutoRenew(false);
                userSubscriptionRepository.save(renewalTarget);

                log.info("✅ Auto-renewal cancelled for user {}. Subscription remains active until {}",
                                userId, subscription.getEndDate());

                notificationService.createNotification(
                                userId,
                                "Hủy gia hạn tự động",
                                !pendingScheduled.isEmpty()
                                                ? "Bạn đã tắt gia hạn tự động cho gói sẽ áp dụng sau khi chuyển. Gói hiện tại vẫn có hiệu lực đến "
                                                                + subscription.getEndDate().toLocalDate()
                                                : "Bạn đã hủy gia hạn tự động gói Premium thành công. Gói của bạn vẫn có hiệu lực đến "
                                                                + subscription.getEndDate().toLocalDate(),
                                NotificationType.PREMIUM_CANCEL,
                                String.valueOf(renewalTarget.getId()));
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
                                                PremiumConstants.MSG_NO_ACTIVE_SUBSCRIPTION));

                // 2. Check if Free tier (cannot cancel/refund)
                if (subscription.getPlan().getPlanType() == PremiumPlan.PlanType.FREE_TIER) {
                        throw new RuntimeException(PremiumConstants.MSG_FREE_TIER_NO_REFUND);
                }

                // 2.5. Check cancellation limit (max 1 time per month)
                String currentMonth = SubscriptionCancellation.getCurrentMonth();
                Long cancellationsThisMonth = cancellationRepository.countByUserAndCancellationMonth(user,
                                currentMonth);
                if (cancellationsThisMonth >= 1) {
                        throw new RuntimeException(
                                        PremiumConstants.MSG_CANCELLATION_LIMIT + " Vui lòng thử lại vào tháng sau.");
                }

                // 3. Calculate days since purchase
                LocalDateTime purchaseDate = subscription.getStartDate();
                LocalDateTime now = LocalDateTime.now();
                long hoursSincePurchase = Duration.between(purchaseDate, now).toHours();
                long daysSincePurchase = hoursSincePurchase / 24;

                // 4. Calculate refund percentage based on usage time
                int refundPercentage;
                if (hoursSincePurchase <= PremiumConstants.FULL_REFUND_HOURS) {
                        refundPercentage = 100; // Within 24h: 100% refund
                } else if (hoursSincePurchase <= PremiumConstants.PARTIAL_REFUND_HOURS) {
                        refundPercentage = PremiumConstants.PARTIAL_REFUND_PERCENTAGE; // 24h-72h: 50% refund
                } else {
                        refundPercentage = 0; // Over 72h: No refund, just cancel auto-renewal
                }

                // 5. Calculate actual refund amount
                BigDecimal originalPrice = resolveRefundBaseAmount(subscription);

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

                        walletService.processRefund(userId, refundAmount, refundDescription,
                                        "SUBSCRIPTION_REFUND", referenceId);

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
                        return new RefundEligibility(false, 0, 0.0, 0, PremiumConstants.MSG_USER_NOT_FOUND);
                }

                var subscriptionOpt = userSubscriptionRepository.findCurrentActiveSubscription(user);
                if (subscriptionOpt.isEmpty()) {
                        return new RefundEligibility(false, 0, 0.0, 0, PremiumConstants.MSG_NO_ACTIVE_SUBSCRIPTION);
                }

                UserSubscription subscription = subscriptionOpt.get();

                if (subscription.getPlan().getPlanType() == PremiumPlan.PlanType.FREE_TIER) {
                        return new RefundEligibility(false, 0, 0.0, 0, PremiumConstants.MSG_FREE_TIER_NO_REFUND);
                }

                // CHECK CANCELLATION LIMIT FIRST
                String currentMonth = SubscriptionCancellation.getCurrentMonth();
                Long cancellationsThisMonth = cancellationRepository.countByUserAndCancellationMonth(user,
                                currentMonth);
                if (cancellationsThisMonth >= 1) {
                        return new RefundEligibility(
                                        false,
                                        0,
                                        0.0,
                                        0,
                                        PremiumConstants.MSG_CANCELLATION_LIMIT + " Vui lòng thử lại vào tháng sau.");
                }

                // Calculate time since purchase
                LocalDateTime purchaseDate = subscription.getStartDate();
                LocalDateTime now = LocalDateTime.now();
                long hoursSincePurchase = Duration.between(purchaseDate, now).toHours();
                long daysSincePurchase = hoursSincePurchase / 24;

                // Determine refund percentage
                int refundPercentage;
                String message;
                if (hoursSincePurchase <= PremiumConstants.FULL_REFUND_HOURS) {
                        refundPercentage = 100;
                        message = PremiumConstants.MSG_REFUND_FULL;
                } else if (hoursSincePurchase <= PremiumConstants.PARTIAL_REFUND_HOURS) {
                        refundPercentage = PremiumConstants.PARTIAL_REFUND_PERCENTAGE;
                        message = PremiumConstants.MSG_REFUND_PARTIAL;
                } else {
                        refundPercentage = 0;
                        message = PremiumConstants.MSG_REFUND_EXPIRED;
                }

                // Calculate refund amount
                BigDecimal originalPrice = resolveRefundBaseAmount(subscription);

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
                        log.info("No direct COMPLETED premium payments found for user {}. Checking gift/parent payments...", userId);
                        completedPayments = paymentTransactionRepository.findByTypeAndStatus(
                                        PaymentTransaction.PaymentType.PREMIUM_SUBSCRIPTION,
                                        PaymentTransaction.PaymentStatus.COMPLETED);
                        if (completedPayments.isEmpty()) {
                                log.info("No COMPLETED premium payments found in system for recovery of user {}", userId);
                                return false;
                        }
                }

                // Try to match pending subscriptions with completed payments via metadata
                for (PaymentTransaction payment : completedPayments) {
                        Optional<Long> subscriptionIdOpt = extractSubscriptionIdFromMetadata(payment.getMetadata());
                        if (subscriptionIdOpt.isEmpty()) {
                                continue;
                        }

                        Long subscriptionId = subscriptionIdOpt.get();

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
                }

                log.info("No recoverable subscriptions found for user {}", userId);
                return false;
        }

        @Override
        @Transactional
        public void rollbackPendingSubscriptionPayment(String paymentMetadata, String reason) {
                Optional<Long> subscriptionIdOpt = extractSubscriptionIdFromMetadata(paymentMetadata);
                if (subscriptionIdOpt.isEmpty()) {
                        log.debug("No subscriptionId found in payment metadata, skipping pending subscription rollback");
                        return;
                }

                Long subscriptionId = subscriptionIdOpt.get();
                UserSubscription subscription = userSubscriptionRepository.findById(subscriptionId)
                                .orElseThrow(() -> new RuntimeException("Subscription not found: " + subscriptionId));

                if (subscription.getStatus() != UserSubscription.SubscriptionStatus.PENDING) {
                        log.info("Skipping rollback for subscription {} because status is {}", subscriptionId,
                                        subscription.getStatus());
                        return;
                }

                log.info("Rolling back pending premium subscription {} due to payment failure/cancellation", subscriptionId);

                subscription.cancel(reason != null ? reason : "Payment cancelled or failed before activation");
                userSubscriptionRepository.save(subscription);

                try {
                        assignFreeTierIfMissing(subscription.getUser().getId());
                } catch (Exception e) {
                        log.error("Failed to restore fallback subscription for user {} after rollback of subscription {}: {}",
                                        subscription.getUser().getId(), subscriptionId, e.getMessage());
                }
        }

        private Optional<Long> extractSubscriptionIdFromMetadata(String metadata) {
                return extractLongFieldFromMetadata(metadata, "subscriptionId");
        }

        private Optional<String> extractTextFieldFromMetadata(String metadata, String fieldName) {
                if (metadata == null || metadata.isBlank()) {
                        return Optional.empty();
                }

                try {
                        JsonNode node = objectMapper.readTree(metadata);
                        JsonNode fieldNode = node.get(fieldName);
                        if (fieldNode == null || fieldNode.isNull()) {
                                return Optional.empty();
                        }
                        return Optional.of(fieldNode.asText());
                } catch (Exception e) {
                        log.warn("Failed to parse {} from metadata: {}", fieldName, metadata);
                        return Optional.empty();
                }
        }

        private Optional<Long> extractLongFieldFromMetadata(String metadata, String fieldName) {
                if (metadata == null || metadata.isBlank()) {
                        return Optional.empty();
                }

                try {
                        JsonNode node = objectMapper.readTree(metadata);
                        JsonNode fieldNode = node.get(fieldName);
                        if (fieldNode == null || fieldNode.isNull()) {
                                return Optional.empty();
                        }
                        return Optional.of(fieldNode.asLong());
                } catch (Exception e) {
                        log.warn("Failed to parse {} from metadata: {}", fieldName, metadata);
                        return Optional.empty();
                }
        }

        private void validatePlanEligibility(User recipient, PremiumPlan plan) {
                if (recipient == null) {
                        throw new RuntimeException("Người nhận không hợp lệ.");
                }
                if (plan == null) {
                        throw new RuntimeException("Gói Premium không tồn tại.");
                }
                if (!Boolean.TRUE.equals(plan.getIsActive())) {
                        throw new RuntimeException("Gói Premium hiện không khả dụng.");
                }
                PremiumPlan.TargetRole recipientRole = resolveTargetRoleByPrimaryRole(recipient.getPrimaryRole());
                if (!isPlanPurchasableForRole(plan, recipientRole)) {
                        throw new RuntimeException("Gói này không dành cho loại tài khoản của người nhận.");
                }

                if (plan.getPlanType() == PremiumPlan.PlanType.STUDENT_PACK
                                && !studentVerificationService.hasApprovedStudentVerification(recipient.getId())) {
                        throw new RuntimeException("Bạn cần hoàn tất xác thực sinh viên trước khi mua gói Student Pack.");
                }
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

                return isPlanVisibleForRole(plan, targetRole, includeFreeTier);
        }

        private boolean isPlanVisibleForRole(
                        PremiumPlan plan,
                        PremiumPlan.TargetRole viewerRole,
                        boolean includeFreeTier) {
                if (plan == null || viewerRole == null) {
                        return false;
                }

                if (plan.getPlanType() == PremiumPlan.PlanType.FREE_TIER) {
                        return includeFreeTier;
                }

                return resolvePlanTargetRole(plan) == viewerRole;
        }

        private PremiumPlan.TargetRole resolveTargetRoleByPrimaryRole(PrimaryRole primaryRole) {
                if (primaryRole == PrimaryRole.RECRUITER) {
                        return PremiumPlan.TargetRole.RECRUITER;
                }
                return PremiumPlan.TargetRole.LEARNER;
        }

        private PremiumPlan.TargetRole resolvePlanTargetRole(PremiumPlan plan) {
                if (plan == null) {
                        return PremiumPlan.TargetRole.LEARNER;
                }
                if (plan.getPlanType() == PremiumPlan.PlanType.RECRUITER_PRO) {
                        return PremiumPlan.TargetRole.RECRUITER;
                }
                return normalizeLegacyTargetRole(plan.getTargetRole());
        }

        private boolean isPlanPurchasableForRole(
                        PremiumPlan plan,
                        PremiumPlan.TargetRole recipientRole) {
                if (plan == null || recipientRole == null) {
                        return false;
                }
                if (plan.getPlanType() == PremiumPlan.PlanType.FREE_TIER) {
                        return true;
                }
                return resolvePlanTargetRole(plan) == recipientRole;
        }

        private PremiumPlan.TargetRole normalizeLegacyTargetRole(PremiumPlan.TargetRole targetRole) {
                if (targetRole == null) {
                        return PremiumPlan.TargetRole.LEARNER;
                }
                return targetRole;
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
