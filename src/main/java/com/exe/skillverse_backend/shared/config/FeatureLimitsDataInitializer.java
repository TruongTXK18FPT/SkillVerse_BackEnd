package com.exe.skillverse_backend.shared.config;

import com.exe.skillverse_backend.premium_service.entity.*;
import com.exe.skillverse_backend.premium_service.repository.PlanFeatureLimitsRepository;
import com.exe.skillverse_backend.premium_service.repository.PremiumPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Initialize default feature limits for premium plans
 * Currently only initializes FREE_TIER limits
 */
@Component
@Order(3) // Run after DataInitializer (Order 2)
@RequiredArgsConstructor
@Slf4j
public class FeatureLimitsDataInitializer implements CommandLineRunner {

        private final PremiumPlanRepository premiumPlanRepository;
        private final PlanFeatureLimitsRepository featureLimitsRepository;

        @Override
        @Transactional
        public void run(String... args) {
                log.info("🚀 [ORDER 3] FeatureLimitsDataInitializer starting...");
                log.info("🔧 Initializing feature limits for FREE_TIER...");

                // Get FREE_TIER plan
                Optional<PremiumPlan> freeTierOpt = premiumPlanRepository
                                .findByPlanTypeAndIsActiveTrue(PremiumPlan.PlanType.FREE_TIER);

                if (freeTierOpt.isEmpty()) {
                        log.warn("⚠️ FREE_TIER plan not found, skipping feature limits initialization");
                        return;
                }

                PremiumPlan freeTier = freeTierOpt.get();
                log.info("✅ Found FREE_TIER plan - ID: {}, Name: {}", freeTier.getId(), freeTier.getName());

                // Initialize FREE_TIER limits
                initializeFreeTierLimits(freeTier);

                log.info("✅ [ORDER 3] Feature limits initialization complete");
        }

        /**
         * Initialize limits for FREE_TIER plan
         */
        private void initializeFreeTierLimits(PremiumPlan freeTier) {
                log.info("Initializing FREE_TIER limits for plan: {}", freeTier.getName());

                // AI Chatbot: 10 requests per 8-hour window
                createOrUpdateLimit(
                                freeTier,
                                FeatureType.AI_CHATBOT_REQUESTS,
                                10,
                                ResetPeriod.CUSTOM_8_HOURS,
                                false,
                                null,
                                "10 chat requests per 8-hour window (resets 8 hours after first request)",
                                true);

                // AI Roadmap: 1 generation per day (as per user request)
                createOrUpdateLimit(
                                freeTier,
                                FeatureType.AI_ROADMAP_GENERATION,
                                1,
                                ResetPeriod.DAILY,
                                false,
                                null,
                                "1 roadmap generation per day",
                                true);

                // Mentor Booking: Not available in free tier (set to 1 with isActive=false)
                createOrUpdateLimit(
                                freeTier,
                                FeatureType.MENTOR_BOOKING_MONTHLY,
                                1,
                                ResetPeriod.MONTHLY,
                                false,
                                null,
                                "Mentor booking not available in free tier",
                                false);

                // Coin Earning Multiplier: 1.0x (normal)
                createOrUpdateLimit(
                                freeTier,
                                FeatureType.COIN_EARNING_MULTIPLIER,
                                1,
                                ResetPeriod.NEVER,
                                false,
                                new BigDecimal("1.00"),
                                "Normal coin earning rate (1.0x)",
                                true);

                // Priority Support: Not available in free tier (set to 1 with isActive=false)
                createOrUpdateLimit(
                                freeTier,
                                FeatureType.PRIORITY_SUPPORT,
                                1,
                                ResetPeriod.NEVER,
                                false,
                                null,
                                "Priority support not available in free tier",
                                false);

                log.info("✅ FREE_TIER limits initialized: 5 features configured");
        }

        /**
         * Create or update a feature limit
         */
        private void createOrUpdateLimit(
                        PremiumPlan plan,
                        FeatureType featureType,
                        Integer limitValue,
                        ResetPeriod resetPeriod,
                        Boolean isUnlimited,
                        BigDecimal bonusMultiplier,
                        String description,
                        Boolean isActive) {

                Optional<PlanFeatureLimits> existing = featureLimitsRepository
                                .findByPlanAndFeatureType(plan, featureType);

                if (existing.isPresent()) {
                        // Update existing
                        PlanFeatureLimits limit = existing.get();
                        limit.setLimitValue(limitValue);
                        limit.setResetPeriod(resetPeriod);
                        limit.setIsUnlimited(isUnlimited);
                        limit.setBonusMultiplier(bonusMultiplier != null ? bonusMultiplier : BigDecimal.ONE);
                        limit.setDescription(description);
                        limit.setIsActive(isActive != null ? isActive : true);
                        featureLimitsRepository.save(limit);

                        log.debug("Updated limit: {} = {}", featureType,
                                        limitValue != null ? limitValue : "multiplier");
                } else {
                        // Create new
                        PlanFeatureLimits limit = PlanFeatureLimits.builder()
                                        .plan(plan)
                                        .featureType(featureType)
                                        .limitValue(limitValue)
                                        .resetPeriod(resetPeriod)
                                        .isUnlimited(isUnlimited)
                                        .bonusMultiplier(bonusMultiplier != null ? bonusMultiplier : BigDecimal.ONE)
                                        .description(description)
                                        .isActive(isActive != null ? isActive : true)
                                        .build();

                        featureLimitsRepository.save(limit);

                        log.debug("Created limit: {} = {}", featureType,
                                        limitValue != null ? limitValue : "multiplier");
                }
        }
}
