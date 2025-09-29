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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                .orElseThrow(() -> new RuntimeException("Premium plan not found: " + request.getPlanId()));

        Optional<UserSubscription> existingSubscription = userSubscriptionRepository
                .findByUserAndIsActiveTrue(user);

        if (existingSubscription.isPresent()) {
            throw new RuntimeException("User already has an active subscription");
        }

        boolean isStudentEligible = request.getApplyStudentDiscount() &&
                isValidStudentEmail(user.getEmail());

        LocalDateTime startDate = LocalDateTime.now();
        LocalDateTime endDate = startDate.plusMonths(plan.getDurationMonths());

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
        return userSubscriptionRepository.hasActiveSubscription(user, LocalDateTime.now());
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
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void processExpiredSubscriptions() {
        userSubscriptionRepository.markExpiredSubscriptions(LocalDateTime.now());
        log.info("Processed expired subscriptions at {}", LocalDateTime.now());
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
                .features(plan.getFeatures() != null ? List.of(plan.getFeatures().split(",")) : List.of())
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
                        subscription.getPaymentTransaction() != null ? subscription.getPaymentTransaction().getId()
                                : null)
                .daysRemaining(subscription.getDaysRemaining())
                .currentlyActive(subscription.isCurrentlyActive())
                .cancellationReason(subscription.getCancellationReason())
                .cancelledAt(subscription.getCancelledAt())
                .createdAt(subscription.getCreatedAt())
                .build();
    }
}
