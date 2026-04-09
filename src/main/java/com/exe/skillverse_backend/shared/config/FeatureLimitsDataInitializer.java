package com.exe.skillverse_backend.shared.config;

import com.exe.skillverse_backend.premium_service.entity.FeatureType;
import com.exe.skillverse_backend.premium_service.entity.PlanFeatureLimits;
import com.exe.skillverse_backend.premium_service.entity.PremiumPlan;
import com.exe.skillverse_backend.premium_service.entity.ResetPeriod;
import com.exe.skillverse_backend.premium_service.repository.PlanFeatureLimitsRepository;
import com.exe.skillverse_backend.premium_service.repository.PremiumPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
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
        private final JdbcTemplate jdbcTemplate;

        @Override
        @Transactional
        public void run(String... args) {
                log.info("🚀 [ORDER 3] FeatureLimitsDataInitializer starting...");

                // Fix feature_type constraint to include new types
                fixFeatureTypeConstraint();

                log.info("🔧 Initializing feature limits for FREE_TIER and RECRUITER_PRO...");

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

                // Initialize RECRUITER_PRO limits (for all recruiter plans)
                List<PremiumPlan> recruiterPlans = premiumPlanRepository
                                .findAllByPlanTypeAndIsActiveTrue(PremiumPlan.PlanType.RECRUITER_PRO);
                for (PremiumPlan recruiterPlan : recruiterPlans) {
                        log.info("🔧 Initializing RECRUITER_PRO limits for plan: {} (ID: {})",
                                        recruiterPlan.getName(), recruiterPlan.getId());
                        initializeRecruiterProLimits(recruiterPlan);
                }

                // Also initialize PREMIUM_PLUS limits with job posting quota
                // This allows recruiters who bought PREMIUM_PLUS to also post jobs
                List<PremiumPlan> premiumPlusPlans = premiumPlanRepository
                                .findAllByPlanTypeAndIsActiveTrue(PremiumPlan.PlanType.PREMIUM_PLUS);
                for (PremiumPlan plusPlan : premiumPlusPlans) {
                        log.info("🔧 Initializing PREMIUM_PLUS limits for plan: {} (ID: {})",
                                        plusPlan.getName(), plusPlan.getId());
                        initializePremiumPlusLimits(plusPlan);
                }

                // Initialize PREMIUM_BASIC limits (limited job posting)
                List<PremiumPlan> premiumBasicPlans = premiumPlanRepository
                                .findAllByPlanTypeAndIsActiveTrue(PremiumPlan.PlanType.PREMIUM_BASIC);
                for (PremiumPlan basicPlan : premiumBasicPlans) {
                        log.info("🔧 Initializing PREMIUM_BASIC limits for plan: {} (ID: {})",
                                        basicPlan.getName(), basicPlan.getId());
                        initializePremiumBasicLimits(basicPlan);
                }

                log.info("✅ [ORDER 3] Feature limits initialization complete");
        }

        /**
         * Fix the feature_type CHECK constraint to include new feature types
         */
        private void fixFeatureTypeConstraint() {
                try {
                        log.info("🔧 Fixing plan_feature_limits feature_type constraint...");
                        jdbcTemplate.execute(
                                        "ALTER TABLE plan_feature_limits DROP CONSTRAINT IF EXISTS plan_feature_limits_feature_type_check");
                        jdbcTemplate.execute(
                                        "ALTER TABLE plan_feature_limits ADD CONSTRAINT plan_feature_limits_feature_type_check " +
                                                        "CHECK (feature_type IN (" +
                                                        "'AI_CHATBOT_REQUESTS', 'AI_ROADMAP_GENERATION', 'MENTOR_BOOKING_MONTHLY', " +
                                                        "'COIN_EARNING_MULTIPLIER', 'PRIORITY_SUPPORT', " +
                                                        "'JOB_POSTING_MONTHLY', 'SHORT_TERM_JOB_POSTING', 'HIGHLIGHT_JOB_POST', " +
                                                        "'AI_CANDIDATE_SUGGESTION', 'COMPANY_PROFILE_PREMIUM', 'ANALYTICS_DASHBOARD', " +
                                                        "'CANDIDATE_DATABASE_ACCESS', 'JOB_BOOST_MONTHLY', 'AUTOMATED_OUTREACH', " +
                                                        "'BULK_IMPORT_CANDIDATES', 'API_ACCESS', 'RECRUITER_PRIORITY_SUPPORT'))");
                        log.info("✅ plan_feature_limits feature_type constraint updated");
                } catch (Exception e) {
                        log.warn("⚠️ Could not fix plan_feature_limits feature_type constraint: {}", e.getMessage());
                }

                // Also fix the constraint on user_usage_tracking table
                try {
                        log.info("🔧 Fixing user_usage_tracking feature_type constraint...");
                        jdbcTemplate.execute(
                                        "ALTER TABLE user_usage_tracking DROP CONSTRAINT IF EXISTS user_usage_tracking_feature_type_check");
                        jdbcTemplate.execute(
                                        "ALTER TABLE user_usage_tracking ADD CONSTRAINT user_usage_tracking_feature_type_check " +
                                                        "CHECK (feature_type IN (" +
                                                        "'AI_CHATBOT_REQUESTS', 'AI_ROADMAP_GENERATION', 'MENTOR_BOOKING_MONTHLY', " +
                                                        "'COIN_EARNING_MULTIPLIER', 'PRIORITY_SUPPORT', " +
                                                        "'JOB_POSTING_MONTHLY', 'SHORT_TERM_JOB_POSTING', 'HIGHLIGHT_JOB_POST', " +
                                                        "'AI_CANDIDATE_SUGGESTION', 'COMPANY_PROFILE_PREMIUM', 'ANALYTICS_DASHBOARD', " +
                                                        "'CANDIDATE_DATABASE_ACCESS', 'JOB_BOOST_MONTHLY', 'AUTOMATED_OUTREACH', " +
                                                        "'BULK_IMPORT_CANDIDATES', 'API_ACCESS', 'RECRUITER_PRIORITY_SUPPORT'))");
                        log.info("✅ user_usage_tracking feature_type constraint updated");
                } catch (Exception e) {
                        log.warn("⚠️ Could not fix user_usage_tracking feature_type constraint: {}", e.getMessage());
                }
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
         * Initialize limits for a RECRUITER plan based on plan name:
         * - recruiter_explorer_monthly: 5 FT + 10 ST posts/month, basic features
         * - recruiter_business_monthly: 20 FT + 50 ST posts/month, highlight, analytics
         * - recruiter_enterprise_monthly/yearly: unlimited, all features
         */
        private void initializeRecruiterProLimits(PremiumPlan recruiterPlan) {
                String planName = recruiterPlan.getName();
                log.info("Initializing RECRUITER limits for plan: {}", planName);

                boolean isPlus = planName.contains("plus");
                boolean isEnterprise = planName.contains("enterprise");

                // ── Full-time Job Posting quota ──
                if (isEnterprise) {
                        // Enterprise: unlimited
                        createOrUpdateLimit(recruiterPlan, FeatureType.JOB_POSTING_MONTHLY,
                                        null, ResetPeriod.MONTHLY, true, null,
                                        "Đăng tin tuyển dụng dài hạn không giới hạn", true);
                        createOrUpdateLimit(recruiterPlan, FeatureType.SHORT_TERM_JOB_POSTING,
                                        null, ResetPeriod.MONTHLY, true, null,
                                        "Đăng tin công việc ngắn hạn/gig không giới hạn", true);
                } else if (isPlus) {
                        // Plus: 30 FT/month, 10 ST/month (as per DataInitializer)
                        createOrUpdateLimit(recruiterPlan, FeatureType.JOB_POSTING_MONTHLY,
                                        30, ResetPeriod.MONTHLY, false, null,
                                        "30 tin tuyển dụng dài hạn mỗi tháng", true);
                        createOrUpdateLimit(recruiterPlan, FeatureType.SHORT_TERM_JOB_POSTING,
                                        10, ResetPeriod.MONTHLY, false, null,
                                        "10 tin công việc ngắn hạn mỗi tháng", true);
                } else {
                        // Explorer: 5 FT/month, 10 ST/month
                        createOrUpdateLimit(recruiterPlan, FeatureType.JOB_POSTING_MONTHLY,
                                        5, ResetPeriod.MONTHLY, false, null,
                                        "5 tin tuyển dụng dài hạn mỗi tháng", true);
                        createOrUpdateLimit(recruiterPlan, FeatureType.SHORT_TERM_JOB_POSTING,
                                        10, ResetPeriod.MONTHLY, false, null,
                                        "10 tin công việc ngắn hạn mỗi tháng", true);
                }

                // ── Job Boost (Monthly) ──
                if (isEnterprise) {
                        createOrUpdateLimit(recruiterPlan, FeatureType.JOB_BOOST_MONTHLY,
                                        10, ResetPeriod.MONTHLY, false, null,
                                        "10 lần đẩy tin lên đầu mỗi tháng", true);
                } else if (isPlus) {
                        createOrUpdateLimit(recruiterPlan, FeatureType.JOB_BOOST_MONTHLY,
                                        3, ResetPeriod.MONTHLY, false, null,
                                        "3 lần đẩy tin lên đầu mỗi tháng", true);
                } else {
                        createOrUpdateLimit(recruiterPlan, FeatureType.JOB_BOOST_MONTHLY,
                                        1, ResetPeriod.MONTHLY, false, null,
                                        "1 lần đẩy tin lên đầu mỗi tháng", true);
                }

                // ── Highlight Job Post ──
                createOrUpdateLimit(recruiterPlan, FeatureType.HIGHLIGHT_JOB_POST,
                                1, ResetPeriod.NEVER, false, null,
                                "Đánh dấu nổi bật tin tuyển dụng trên trang /jobs",
                                isPlus || isEnterprise);

                // ── AI Candidate Suggestion ──
                createOrUpdateLimit(recruiterPlan, FeatureType.AI_CANDIDATE_SUGGESTION,
                                1, ResetPeriod.NEVER, false, null,
                                "AI gợi ý ứng viên phù hợp với công việc",
                                isEnterprise);

                // ── Premium Company Profile ──
                createOrUpdateLimit(recruiterPlan, FeatureType.COMPANY_PROFILE_PREMIUM,
                                1, ResetPeriod.NEVER, false, null,
                                "Hồ sơ công ty nâng cao với logo, banner, video",
                                isPlus || isEnterprise);

                // ── Analytics Dashboard ──
                createOrUpdateLimit(recruiterPlan, FeatureType.ANALYTICS_DASHBOARD,
                                1, ResetPeriod.NEVER, false, null,
                                "Bảng phân tích chi tiết tuyển dụng",
                                isPlus || isEnterprise);

                // ── Candidate Database Access ──
                createOrUpdateLimit(recruiterPlan, FeatureType.CANDIDATE_DATABASE_ACCESS,
                                1, ResetPeriod.NEVER, false, null,
                                "Truy cập cơ sở dữ liệu ứng viên",
                                isEnterprise);

                // ── Automated Outreach ──
                createOrUpdateLimit(recruiterPlan, FeatureType.AUTOMATED_OUTREACH,
                                1, ResetPeriod.NEVER, false, null,
                                "Tự động tiếp cận ứng viên phù hợp",
                                isEnterprise);

                // ── Bulk Import Candidates ──
                if (isEnterprise) {
                        createOrUpdateLimit(recruiterPlan, FeatureType.BULK_IMPORT_CANDIDATES,
                                        500, ResetPeriod.MONTHLY, false, null,
                                        "Nhập khẩu 500 ứng viên mỗi tháng", true);
                } else if (isPlus) {
                        createOrUpdateLimit(recruiterPlan, FeatureType.BULK_IMPORT_CANDIDATES,
                                        100, ResetPeriod.MONTHLY, false, null,
                                        "Nhập khẩu 100 ứng viên mỗi tháng", true);
                } else {
                        createOrUpdateLimit(recruiterPlan, FeatureType.BULK_IMPORT_CANDIDATES,
                                        20, ResetPeriod.MONTHLY, false, null,
                                        "Nhập khẩu 20 ứng viên mỗi tháng", true);
                }

                // ── API Access ──
                createOrUpdateLimit(recruiterPlan, FeatureType.API_ACCESS,
                                1, ResetPeriod.NEVER, false, null,
                                "Truy cập API để tích hợp hệ thống",
                                isEnterprise);

                // ── Recruiter Priority Support ──
                createOrUpdateLimit(recruiterPlan, FeatureType.RECRUITER_PRIORITY_SUPPORT,
                                1, ResetPeriod.NEVER, false, null,
                                "Hỗ trợ ưu tiên dành riêng cho recruiter",
                                isPlus || isEnterprise);

                log.info("✅ RECRUITER limits initialized for {}: ft={}, st={}, boost={}, highlight={}, ai={}, analytics={}, priority={}",
                                planName,
                                isEnterprise ? "unlimited" : (isPlus ? "30" : "5"),
                                isEnterprise ? "unlimited" : (isPlus ? "10" : "10"),
                                isEnterprise ? "10" : (isPlus ? "3" : "1"),
                                isPlus || isEnterprise,
                                isEnterprise,
                                isPlus || isEnterprise,
                                isPlus || isEnterprise);
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

        /**
         * Initialize limits for PREMIUM_PLUS plan - includes job posting quota
         * This allows users with PREMIUM_PLUS to also use as recruiter
         */
        private void initializePremiumPlusLimits(PremiumPlan plan) {
                log.info("Initializing PREMIUM_PLUS limits for plan: {}", plan.getName());

                // Full-time Job Posting: 30/month
                createOrUpdateLimit(plan, FeatureType.JOB_POSTING_MONTHLY,
                                30, ResetPeriod.MONTHLY, false, null,
                                "30 tin tuyển dụng dài hạn mỗi tháng", true);

                // Short-term Job Posting: 10/month
                createOrUpdateLimit(plan, FeatureType.SHORT_TERM_JOB_POSTING,
                                10, ResetPeriod.MONTHLY, false, null,
                                "10 tin công việc ngắn hạn mỗi tháng", true);

                // Job Boost: 3/month
                createOrUpdateLimit(plan, FeatureType.JOB_BOOST_MONTHLY,
                                3, ResetPeriod.MONTHLY, false, null,
                                "3 lần đẩy tin lên đầu mỗi tháng", true);

                // Highlight Job Post - enabled for PREMIUM_PLUS
                createOrUpdateLimit(plan, FeatureType.HIGHLIGHT_JOB_POST,
                                1, ResetPeriod.NEVER, false, null,
                                "Đánh dấu nổi bật tin tuyển dụng", true);

                // AI Candidate Suggestion - not for PLUS
                createOrUpdateLimit(plan, FeatureType.AI_CANDIDATE_SUGGESTION,
                                1, ResetPeriod.NEVER, false, null,
                                "AI gợi ý ứng viên phù hợp", false);

                // Premium Company Profile - enabled for PLUS
                createOrUpdateLimit(plan, FeatureType.COMPANY_PROFILE_PREMIUM,
                                1, ResetPeriod.NEVER, false, null,
                                "Hồ sơ công ty nâng cao", true);

                // Analytics Dashboard - enabled for PLUS
                createOrUpdateLimit(plan, FeatureType.ANALYTICS_DASHBOARD,
                                1, ResetPeriod.NEVER, false, null,
                                "Bảng phân tích chi tiết", true);

                // Priority Support - enabled for PLUS
                createOrUpdateLimit(plan, FeatureType.RECRUITER_PRIORITY_SUPPORT,
                                1, ResetPeriod.NEVER, false, null,
                                "Hỗ trợ ưu tiên", true);

                log.info("✅ PREMIUM_PLUS limits initialized: jobPosting=30, shortTerm=10, boost=3, highlight=true");
        }

        /**
         * Initialize limits for PREMIUM_BASIC plan - limited job posting quota
         */
        private void initializePremiumBasicLimits(PremiumPlan plan) {
                log.info("Initializing PREMIUM_BASIC limits for plan: {}", plan.getName());

                // Full-time Job Posting: 10/month (limited)
                createOrUpdateLimit(plan, FeatureType.JOB_POSTING_MONTHLY,
                                10, ResetPeriod.MONTHLY, false, null,
                                "10 tin tuyển dụng dài hạn mỗi tháng", true);

                // Short-term Job Posting: 5/month
                createOrUpdateLimit(plan, FeatureType.SHORT_TERM_JOB_POSTING,
                                5, ResetPeriod.MONTHLY, false, null,
                                "5 tin công việc ngắn hạn mỗi tháng", true);

                // Job Boost: 1/month
                createOrUpdateLimit(plan, FeatureType.JOB_BOOST_MONTHLY,
                                1, ResetPeriod.MONTHLY, false, null,
                                "1 lần đẩy tin lên đầu mỗi tháng", true);

                // Highlight Job Post - disabled for BASIC
                createOrUpdateLimit(plan, FeatureType.HIGHLIGHT_JOB_POST,
                                1, ResetPeriod.NEVER, false, null,
                                "Đánh dấu nổi bật tin tuyển dụng", false);

                // AI Candidate Suggestion - not for BASIC
                createOrUpdateLimit(plan, FeatureType.AI_CANDIDATE_SUGGESTION,
                                1, ResetPeriod.NEVER, false, null,
                                "AI gợi ý ứng viên phù hợp", false);

                // Premium Company Profile - disabled for BASIC
                createOrUpdateLimit(plan, FeatureType.COMPANY_PROFILE_PREMIUM,
                                1, ResetPeriod.NEVER, false, null,
                                "Hồ sơ công ty nâng cao", false);

                // Analytics Dashboard - disabled for BASIC
                createOrUpdateLimit(plan, FeatureType.ANALYTICS_DASHBOARD,
                                1, ResetPeriod.NEVER, false, null,
                                "Bảng phân tích chi tiết", false);

                // Priority Support - disabled for BASIC
                createOrUpdateLimit(plan, FeatureType.RECRUITER_PRIORITY_SUPPORT,
                                1, ResetPeriod.NEVER, false, null,
                                "Hỗ trợ ưu tiên", false);

                log.info("✅ PREMIUM_BASIC limits initialized: jobPosting=10, shortTerm=5, boost=1");
        }
}
