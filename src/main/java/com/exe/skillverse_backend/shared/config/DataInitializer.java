package com.exe.skillverse_backend.shared.config;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.exe.skillverse_backend.auth_service.entity.Role;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.RoleRepository;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.entity.UserStatus;
import com.exe.skillverse_backend.auth_service.entity.AuthProvider;
import com.exe.skillverse_backend.premium_service.entity.PremiumPlan;
import com.exe.skillverse_backend.premium_service.repository.PremiumPlanRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Order(1) // Run first - create roles, users, and FREE_TIER plan
@Slf4j
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

        private final RoleRepository roleRepository;
        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final PremiumPlanRepository premiumPlanRepository;
        private final JdbcTemplate jdbcTemplate;

        @Override
        public void run(String... args) throws Exception {
                log.info("🚀 [ORDER 1] DataInitializer starting...");
                fixDatabaseConstraints();
                initializeRoles();
                log.info("⏭️ Skipping user seed for production-ready startup");
                initializePremiumPlans();
                log.info("✅ [ORDER 1] DataInitializer completed");
                // initializeProfiles(); // Temporarily disabled - profiles can be created via
                // API
        }

        private void fixDatabaseConstraints() {
                try {
                        log.info("🔧 Fixing database constraints...");

                        // Fix users_primary_role_check
                        String dropConstraintSql = "ALTER TABLE users DROP CONSTRAINT IF EXISTS users_primary_role_check";
                        jdbcTemplate.execute(dropConstraintSql);

                        String addConstraintSql = "ALTER TABLE users ADD CONSTRAINT users_primary_role_check " +
                                        "CHECK (primary_role IN ('USER', 'MENTOR', 'RECRUITER', 'PARENT', 'ADMIN', " +
                                        "'USER_ADMIN', 'CONTENT_ADMIN', 'COMMUNITY_ADMIN', 'FINANCE_ADMIN', " +
                                        "'PREMIUM_ADMIN', 'AI_ADMIN', 'SUPPORT_ADMIN', 'SYSTEM_ADMIN'))";
                        jdbcTemplate.execute(addConstraintSql);

                        // Fix premium_plans_plan_type_check — add RECRUITER_PRO
                        String dropPlanTypeConstraint = "ALTER TABLE premium_plans DROP CONSTRAINT IF EXISTS premium_plans_plan_type_check";
                        jdbcTemplate.execute(dropPlanTypeConstraint);

                        String addPlanTypeConstraint = "ALTER TABLE premium_plans ADD CONSTRAINT premium_plans_plan_type_check " +
                                        "CHECK (plan_type IN ('FREE_TIER', 'PREMIUM_BASIC', 'PREMIUM_PLUS', 'STUDENT_PACK', 'RECRUITER_PRO'))";
                        jdbcTemplate.execute(addPlanTypeConstraint);

                        log.info("✅ Database constraints fixed successfully");
                } catch (Exception e) {
                        log.error("⚠️ Failed to fix database constraints: {}", e.getMessage());
                }
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

                        // Create PARENT role if it doesn't exist
                        if (!roleRepository.existsByName("PARENT")) {
                                Role parentRole = new Role();
                                parentRole.setName("PARENT");
                                roleRepository.save(parentRole);
                                log.info("✅ Created PARENT role");
                        }

                        // Initialize Sub-Admin Roles
                        String[] subAdminRoles = {
                            "USER_ADMIN", "CONTENT_ADMIN", "COMMUNITY_ADMIN", 
                            "FINANCE_ADMIN", "PREMIUM_ADMIN", "AI_ADMIN", 
                            "SUPPORT_ADMIN", "SYSTEM_ADMIN"
                        };

                        for (String roleName : subAdminRoles) {
                            if (!roleRepository.existsByName(roleName)) {
                                Role role = new Role();
                                role.setName(roleName);
                                roleRepository.save(role);
                                log.info("✅ Created {} role", roleName);
                            }
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
                        createUserIfNotExists("exerecruiter@gmail.com", "Password123!", Set.of(recruiterRole),
                                        "Recruiter User",
                                        PrimaryRole.RECRUITER, UserStatus.ACTIVE);

                        // Create regular user (ACTIVE after email verification)
                        createUserIfNotExists("exeuser@gmail.com", "Password123!", Set.of(userRole), "Regular User",
                                        PrimaryRole.USER, UserStatus.ACTIVE);

                        // Seed 11 student accounts (Google login), created at 05/12/2025
                        LocalDateTime createdDate = LocalDateTime.of(2025, 12, 5, 0, 0);
                        createGoogleStudentIfNotExists("quanphse161574@fpt.edu.vn", userRole, createdDate,
                                        "Phạm Hồng Quân");
                        createGoogleStudentIfNotExists("baotqhe172635@fpt.edu.vn", userRole, createdDate,
                                        "Trần Quốc Bảo");
                        createGoogleStudentIfNotExists("davethediver0411@gmail.com", userRole, createdDate,
                                        "davethediver0411@gmail.com");
                        createGoogleStudentIfNotExists("tamvmse184878@fpt.edu.vn", userRole, createdDate,
                                        "Vũ Minh Tâm");
                        createGoogleStudentIfNotExists("hoantse183091@fpt.edu.vn", userRole, createdDate,
                                        "Nguyễn Thanh Hòa");
                        createGoogleStudentIfNotExists("nhittysa180180@fpt.edu.vn", userRole, createdDate,
                                        "Tu Thi Yen Nhi (K18 HCM)");
                        createGoogleStudentIfNotExists("kujousara0411@gmail.com", userRole, createdDate,
                                        "kujousara0411@gmail.com");
                        createGoogleStudentIfNotExists("tdat01122004@gmail.com", userRole, createdDate,
                                        "Trần Thành Đạt");
                        createGoogleStudentIfNotExists("phanphucnguyen3003@gmail.com", userRole, createdDate,
                                        "Phạm Phúc Nguyên");
                        createGoogleStudentIfNotExists("nguyenkhanhlinh.april@gmail.com", userRole, createdDate,
                                        "Nguyễn Khánh Linh");
                        createGoogleStudentIfNotExists("khanhlinhngu89@gmail.com", userRole, createdDate,
                                        "Trần Khánh Linh");

                        LocalDateTime createdDateDec4 = LocalDateTime.of(2025, 12, 4, 0, 0);
                        createGoogleStudentIfNotExists("phamnhuy8928@gmail.com", userRole, createdDateDec4,
                                        "Phạm Quang Huy");
                        createGoogleStudentIfNotExists("tutran200823@gmail.com", userRole, createdDateDec4,
                                        "tutran200823@gmail.com");
                        createGoogleStudentIfNotExists("Thaom722@gmail.com", userRole, createdDateDec4, "Minh Thảo");
                        createGoogleStudentIfNotExists("pothei1104@gmail.com", userRole, createdDateDec4,
                                        "pothei1104@gmail.com");
                        createGoogleStudentIfNotExists("quannsse1845831@fpt.edu.vn", userRole, createdDateDec4,
                                        "Nguyễn Sỹ Quân(K18 HCM)");
                        createGoogleStudentIfNotExists("vuhsese182692@fpt.edu.vn", userRole, createdDateDec4,
                                        "Hoàng Vũ (K18 HCM)");
                        createGoogleStudentIfNotExists("quanlvse182728@fpt.edu.vn", userRole, createdDateDec4,
                                        "Lê Văn Quân(K18 HCM)");
                        createGoogleStudentIfNotExists("khanhtgse182983@fpt.edu.vn", userRole, createdDateDec4,
                                        "Trần Giang Khánh (K18 HCM)");
                        createGoogleStudentIfNotExists("giangntse183662@fpt.edu.vn", userRole, createdDateDec4,
                                        "Nguyễn Trường Giang (K18 HCM)");
                        createGoogleStudentIfNotExists("nammhse184557@fpt.edu.vn", userRole, createdDateDec4,
                                        "Mai Hải Nam(K18 HCM)");
                        createGoogleStudentIfNotExists("haomgse184349@fpt.edu.vn", userRole, createdDateDec4,
                                        "Mạch Gia Hào (K18 HCM)");
                        createGoogleStudentIfNotExists("maintpse184343@fpt.edu.vn", userRole, createdDateDec4,
                                        "Nguyễn Thị Phương Mai (K18 HCM)");
                        createGoogleStudentIfNotExists("nhutpmse184520@fpt.edu.vn", userRole, createdDateDec4,
                                        "Phạm Minh Nhựt(K18 HCM)");
                        createGoogleStudentIfNotExists("giapcdse182538@fpt.edu.vn", userRole, createdDateDec4,
                                        "Cao Đình Giáp(K18 HCM)");
                        createGoogleStudentIfNotExists("tranlediemmy0128@gmail.com", userRole, createdDateDec4,
                                        "Trần Lê Diễm My");
                        createGoogleStudentIfNotExists("calamarri0412@gmail.com", userRole, createdDateDec4,
                                        "calamarri0412@gmail.com");
                        createGoogleStudentIfNotExists("luanlemluoc0411@gmail.com", userRole, createdDateDec4,
                                        "Trương Quốc Luân");
                        createGoogleStudentIfNotExists("1905myhien@gmail.com", userRole, createdDateDec4, "Mỹ Hiền");
                        createGoogleStudentIfNotExists("khanghqse182958@fpt.edu.vn", userRole, createdDateDec4,
                                        "Huỳnh Quốc Khang (K18 HCM)");
                        createGoogleStudentIfNotExists("khangqt1801@gmail.com", userRole, createdDateDec4,
                                        "khangqt1801@gmail.com");
                        createGoogleStudentIfNotExists("kietnmss180517@fpt.edu.vn", userRole, createdDateDec4,
                                        "Nguyễn Minh Kiệt (K18 HCM)");
                        createGoogleStudentIfNotExists("hoangtran.lenom@gmail.com", userRole, createdDateDec4,
                                        "hoangtran.lenom@gmail.com");
                        createGoogleStudentIfNotExists("quannmse180261@fpt.edu.vn", userRole, createdDateDec4,
                                        "Nguyễn Minh Quân(K18 HCM)");
                        createGoogleStudentIfNotExists("minhcnse180019@fpt.edu.vn", userRole, createdDateDec4,
                                        "Chu Minh Nhật (K18 HCM)");

                        log.info("🎉 All test users initialized successfully");

                } catch (Exception e) {
                        log.error("❌ Error initializing users: {}", e.getMessage(), e);
                        throw new RuntimeException("Failed to initialize users", e);
                }
        }

        private void initializePremiumPlans() {
                try {
                        // ✅ Create FREE_TIER plan (mandatory for new users)
                        createPremiumPlanIfNotExists(
                                        "free_tier",
                                        "Free Tier",
                                        "Gói miễn phí với quyền truy cập cơ bản",
                                        Integer.MAX_VALUE, // Permanent
                                        new BigDecimal("0"),
                                        PremiumPlan.PlanType.FREE_TIER,
                                        new BigDecimal("0"),
                                        "[\"Truy cập cơ bản\", \"Tham gia cộng đồng\"]");

                        // ✅ Create RECRUITER plans (Plus + Enterprise, monthly + yearly — 4 gói)
                        // Gói Plus Monthly: 30 bài/tháng - Highlight, priority support
                        createPremiumPlanIfNotExists(
                                        "recruiter_plus_monthly",
                                        "Recruiter Plus",
                                        "Gói Recruiter Plus - 30 tin tuyển dụng mỗi tháng, highlight bài đăng",
                                        1, // 1 month
                                        new BigDecimal("149000"), // 149,000 VND/month
                                        PremiumPlan.PlanType.RECRUITER_PRO,
                                        new BigDecimal("0"),
                                        "[\"30 tin tuyển dụng/tháng\", \"10 tin ngắn hạn/tháng\", \"Highlight bài đăng\", \"Hỗ trợ ưu tiên\"]");

                        // Gói Plus Yearly: tiết kiệm ~20%
                        createPremiumPlanIfNotExists(
                                        "recruiter_plus_yearly",
                                        "Recruiter Plus (Năm)",
                                        "Gói Recruiter Plus theo năm - Tiết kiệm 20% so với gói tháng",
                                        12, // 12 months
                                        new BigDecimal("1430000"), // ~119k/tháng, tiết kiệm ~20%
                                        PremiumPlan.PlanType.RECRUITER_PRO,
                                        new BigDecimal("0"),
                                        "[\"30 tin tuyển dụng/tháng\", \"10 tin ngắn hạn/tháng\", \"Highlight bài đăng\", \"Hỗ trợ ưu tiên\", \"Tiết kiệm 20%\"]");

                        // Gói Enterprise Monthly: Không giới hạn, AI gợi ý, analytics
                        createPremiumPlanIfNotExists(
                                        "recruiter_enterprise_monthly",
                                        "Recruiter Enterprise",
                                        "Gói Recruiter Enterprise - Đăng tin không giới hạn, AI gợi ý ứng viên",
                                        1, // 1 month
                                        new BigDecimal("499000"), // 499,000 VND/month
                                        PremiumPlan.PlanType.RECRUITER_PRO,
                                        new BigDecimal("0"),
                                        "[\"Đăng tin không giới hạn\", \"Tin ngắn hạn không giới hạn\", \"AI gợi ý ứng viên\", \"Job Boost 5 lần/tháng\", \"Dashboard phân tích\", \"Hỗ trợ 24/7\"]");

                        // Gói Enterprise Yearly: tiết kiệm ~20%
                        createPremiumPlanIfNotExists(
                                        "recruiter_enterprise_yearly",
                                        "Recruiter Enterprise (Năm)",
                                        "Gói Recruiter Enterprise theo năm - Tiết kiệm 20% so với gói tháng",
                                        12, // 12 months
                                        new BigDecimal("4790000"), // ~399k/tháng, tiết kiệm ~20%
                                        PremiumPlan.PlanType.RECRUITER_PRO,
                                        new BigDecimal("0"),
                                        "[\"Đăng tin không giới hạn\", \"Tin ngắn hạn không giới hạn\", \"AI gợi ý ứng viên\", \"Job Boost 5 lần/tháng\", \"Dashboard phân tích\", \"Hỗ trợ 24/7\", \"Tiết kiệm 20%\"]");

                        log.info("✅ Premium plans initialization completed (FREE_TIER + 4 RECRUITER plans)");
                } catch (Exception e) {
                        log.error("❌ Error initializing premium plans: {}", e.getMessage(), e);
                        throw new RuntimeException("Failed to initialize premium plans", e);
                }
        }

        private void createPremiumPlanIfNotExists(String name, String displayName, String description,
                        Integer durationMonths, BigDecimal price,
                        PremiumPlan.PlanType planType, BigDecimal studentDiscountPercent,
                        String features) {
                var existingPlan = premiumPlanRepository.findByName(name);
                PremiumPlan.TargetRole targetRole = planType == PremiumPlan.PlanType.RECRUITER_PRO
                                ? PremiumPlan.TargetRole.RECRUITER
                                : PremiumPlan.TargetRole.LEARNER;

                if (!existingPlan.isPresent()) {
                        PremiumPlan plan = PremiumPlan.builder()
                                        .name(name)
                                        .displayName(displayName)
                                        .description(description)
                                        .durationMonths(durationMonths)
                                        .price(price)
                                        .currency("VND")
                                        .planType(planType)
                                        .targetRole(targetRole)
                                        .discountPercent(studentDiscountPercent)
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
                        // Update existing plan with new price
                        PremiumPlan plan = existingPlan.get();
                        plan.setDisplayName(displayName);
                        plan.setDescription(description);
                        plan.setDurationMonths(durationMonths);
                        plan.setPrice(price);
                        plan.setCurrency("VND");
                        plan.setPlanType(planType);
                        plan.setTargetRole(targetRole);
                        plan.setDiscountPercent(studentDiscountPercent);
                        plan.setStudentDiscountPercent(studentDiscountPercent);
                        plan.setFeatures(features);
                        plan.setIsActive(true);
                        plan.setMaxSubscribers(null);
                        plan.setUpdatedAt(LocalDateTime.now());

                        premiumPlanRepository.save(plan);
                        log.info("✅ Updated premium plan: {} ({}) - New price: {}", displayName, planType, price);
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

        private void createGoogleStudentIfNotExists(String email, Role userRole, LocalDateTime createdDate,
                        String fullName) {
                if (!userRepository.existsByEmail(email)) {
                        User user = User.builder()
                                        .email(email)
                                        .password(passwordEncoder.encode("Password123!"))
                                        .status(UserStatus.ACTIVE)
                                        .isEmailVerified(true)
                                        .roles(Set.of(userRole))
                                        .primaryRole(PrimaryRole.USER)
                                        .authProvider(AuthProvider.GOOGLE)
                                        .firstName(fullName)
                                        .createdAt(createdDate)
                                        .updatedAt(createdDate)
                                        .build();

                        userRepository.save(user);
                        log.info("✅ Created Google student with email: {}", email);
                } else {
                        log.info("✅ Google student already exists: {}", email);
                }
        }
}
