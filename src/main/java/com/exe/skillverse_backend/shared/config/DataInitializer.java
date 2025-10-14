package com.exe.skillverse_backend.shared.config;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.exe.skillverse_backend.auth_service.entity.Role;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.RoleRepository;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.entity.UserStatus;
import com.exe.skillverse_backend.premium_service.entity.PremiumPlan;
import com.exe.skillverse_backend.premium_service.repository.PremiumPlanRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PremiumPlanRepository premiumPlanRepository;

    @Override
    public void run(String... args) throws Exception {
        initializeRoles();
        initializeUsers();
        initializePremiumPlans();
        // initializeProfiles(); // Temporarily disabled - profiles can be created via
        // API
    }

    private void initializeRoles() {
        try {
            // Create USER role if it doesn't exist
            if (!roleRepository.existsByName("USER")) {
                Role userRole = new Role();
                userRole.setName("USER");
                roleRepository.save(userRole);
                log.info("✅ Created USER role");
            }

            // Create ADMIN role if it doesn't exist
            if (!roleRepository.existsByName("ADMIN")) {
                Role adminRole = new Role();
                adminRole.setName("ADMIN");
                roleRepository.save(adminRole);
                log.info("✅ Created ADMIN role");
            }

            // Create MENTOR role if it doesn't exist
            if (!roleRepository.existsByName("MENTOR")) {
                Role mentorRole = new Role();
                mentorRole.setName("MENTOR");
                roleRepository.save(mentorRole);
                log.info("✅ Created MENTOR role");
            }

            // Create RECRUITER role if it doesn't exist
            if (!roleRepository.existsByName("RECRUITER")) {
                Role recruiterRole = new Role();
                recruiterRole.setName("RECRUITER");
                roleRepository.save(recruiterRole);
                log.info("✅ Created RECRUITER role");
            }

            log.info("🎉 All roles initialized successfully");

        } catch (Exception e) {
            log.error("❌ Error initializing roles: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize roles", e);
        }
    }

    private void initializeUsers() {
        try {
            // Get roles first
            Role userRole = roleRepository.findByName("USER")
                    .orElseThrow(() -> new RuntimeException("USER role not found"));
            Role adminRole = roleRepository.findByName("ADMIN")
                    .orElseThrow(() -> new RuntimeException("ADMIN role not found"));
            Role mentorRole = roleRepository.findByName("MENTOR")
                    .orElseThrow(() -> new RuntimeException("MENTOR role not found"));
            Role recruiterRole = roleRepository.findByName("RECRUITER")
                    .orElseThrow(() -> new RuntimeException("RECRUITER role not found"));

            // Create admin user (admin role with ADMIN primary role)
            createUserIfNotExists("exeadmin@gmail.com", "Password123!", Set.of(adminRole), "Admin User",
                    PrimaryRole.ADMIN, UserStatus.ACTIVE);

            // Create mentor user (ACTIVE for testing - normally would be INACTIVE until
            // approved)
            createUserIfNotExists("exementor@gmail.com", "Password123!", Set.of(mentorRole), "Mentor User",
                    PrimaryRole.MENTOR, UserStatus.ACTIVE);

            // Create recruiter user (ACTIVE for testing - normally would be INACTIVE until
            // approved)
            createUserIfNotExists("exerecruiter@gmail.com", "Password123!", Set.of(recruiterRole), "Recruiter User",
                    PrimaryRole.RECRUITER, UserStatus.ACTIVE);

            // Create regular user (ACTIVE after email verification)
            createUserIfNotExists("exeuser@gmail.com", "Password123!", Set.of(userRole), "Regular User",
                    PrimaryRole.USER, UserStatus.ACTIVE);

            log.info("🎉 All test users initialized successfully");

        } catch (Exception e) {
            log.error("❌ Error initializing users: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize users", e);
        }
    }

    private void initializePremiumPlans() {
        try {
            // Create FREE_TIER plan (permanent)
            createPremiumPlanIfNotExists(
                    "free_tier",
                    "Free Tier",
                    "Gói miễn phí với quyền truy cập cơ bản",
                    Integer.MAX_VALUE, // Permanent
                    new BigDecimal("0"),
                    PremiumPlan.PlanType.FREE_TIER,
                    new BigDecimal("0"),
                    "[\"Truy cập cơ bản\", \"Tham gia cộng đồng\"]");

            // Create PREMIUM_BASIC plan
            createPremiumPlanIfNotExists(
                    "premium_basic",
                    "Premium Basic",
                    "Truy cập khóa học cao cấp và tính năng hướng dẫn cơ bản",
                    1, // 1 month duration
                    new BigDecimal("3000"), // 79,000 VND
                    PremiumPlan.PlanType.PREMIUM_BASIC,
                    new BigDecimal("10"), // 10% student discount
                    "[\"Truy cập khóa học cao cấp\", \"Trò chuyện cơ bản với mentor\", \"Chứng chỉ hoàn thành khóa học\", \"Hỗ trợ ưu tiên\"]");

            // Create PREMIUM_PLUS plan
            createPremiumPlanIfNotExists(
                    "premium_plus",
                    "Premium Plus",
                    "Truy cập đầy đủ tất cả tính năng cao cấp bao gồm hướng dẫn 1-1",
                    3, // 3 months duration
                    new BigDecimal("4000"), // 249,000 VND
                    PremiumPlan.PlanType.PREMIUM_PLUS,
                    new BigDecimal("15"), // 15% student discount
                    "[\"Tất cả tính năng Premium Basic\", \"Hướng dẫn 1-1 không giới hạn\", \"Tư vấn nghề nghiệp\", \"Đánh giá CV\", \"Hỗ trợ tìm việc làm\", \"Hội thảo độc quyền\"]");

            // Create STUDENT plan
            createPremiumPlanIfNotExists(
                    "student",
                    "Student Pack",
                    "Gói ưu đãi đặc biệt cho sinh viên với tính năng cao cấp thiết yếu",
                    1, // 1 month duration
                    new BigDecimal("2000"), // 20,000 VND (already discounted base price)
                    PremiumPlan.PlanType.STUDENT_PACK,
                    new BigDecimal("0"), // No additional discount (already base discounted price)
                    "[\"Truy cập khóa học cao cấp\", \"Truy cập cộng đồng sinh viên\", \"Hướng dẫn cơ bản\", \"Chứng chỉ khóa học\", \"Tải tài liệu học tập\"]");

            log.info("🎉 Tất cả gói premium đã được khởi tạo thành công");

        } catch (Exception e) {
            log.error("❌ Error initializing premium plans: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize premium plans", e);
        }
    }

    private void createPremiumPlanIfNotExists(String name, String displayName, String description,
            Integer durationMonths, BigDecimal price,
            PremiumPlan.PlanType planType, BigDecimal studentDiscountPercent,
            String features) {
        if (!premiumPlanRepository.findByName(name).isPresent()) {
            PremiumPlan plan = PremiumPlan.builder()
                    .name(name)
                    .displayName(displayName)
                    .description(description)
                    .durationMonths(durationMonths)
                    .price(price)
                    .currency("VND")
                    .planType(planType)
                    .studentDiscountPercent(studentDiscountPercent)
                    .features(features)
                    .isActive(true)
                    .maxSubscribers(null) // Unlimited subscribers
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            premiumPlanRepository.save(plan);
            log.info("✅ Created premium plan: {} ({})", displayName, planType);
        } else {
            log.info("✅ Premium plan already exists: {} ({})", displayName, planType);
        }
    }

    private void createUserIfNotExists(String email, String password, Set<Role> roles, String description,
            PrimaryRole primaryRole, UserStatus userStatus) {
        if (!userRepository.existsByEmail(email)) {
            User user = User.builder()
                    .email(email)
                    .password(passwordEncoder.encode(password))
                    .status(userStatus)
                    .isEmailVerified(true)
                    .roles(roles)
                    .primaryRole(primaryRole)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            userRepository.save(user);
            log.info("✅ Created {} with email: {} and primary role: {}", description, email, primaryRole);
        } else {
            log.info("✅ {} already exists: {}", description, email);
        }
    }

}