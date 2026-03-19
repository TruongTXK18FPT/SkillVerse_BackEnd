package com.exe.skillverse_backend.shared.config;

import java.math.BigDecimal;
import java.time.Instant;
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
import com.exe.skillverse_backend.user_service.service.UserProfileService;
import com.exe.skillverse_backend.course_service.entity.Assignment;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.Lesson;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.entity.Quiz;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import com.exe.skillverse_backend.course_service.entity.enums.LessonType;
import com.exe.skillverse_backend.course_service.entity.enums.SubmissionType;
import com.exe.skillverse_backend.course_service.repository.AssignmentRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.repository.LessonRepository;
import com.exe.skillverse_backend.course_service.repository.ModuleRepository;
import com.exe.skillverse_backend.course_service.repository.QuizRepository;

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
        private final CourseRepository courseRepository;
        private final ModuleRepository moduleRepository;
        private final LessonRepository lessonRepository;
        private final QuizRepository quizRepository;
        private final AssignmentRepository assignmentRepository;
        private final UserProfileService userProfileService;
        private final JdbcTemplate jdbcTemplate;

        @Override
        public void run(String... args) throws Exception {
                log.info("🚀 [ORDER 1] DataInitializer starting...");
                fixDatabaseConstraints();
                initializeRoles();
                initializeUsers();
                initializePremiumPlans();
                initializeCourses();
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

        private void initializeCourses() {
                try {
                        // Get mentor user as course author
                        User mentor = userRepository.findByEmail("exementor@gmail.com")
                                        .orElseThrow(() -> new RuntimeException("Mentor user not found"));

                        cleanupDeprecatedMockCourses();

                        // Course 1: Java Programming Fundamentals
                        createCourseIfNotExists(
                                        "Lập trình Java cơ bản",
                                        "Khóa học toàn diện về Java dành cho người mới bắt đầu. Học các khái niệm cơ bản, OOP, và xây dựng ứng dụng thực tế.",
                                        "BEGINNER",
                                        CourseStatus.PUBLIC,
                                        new BigDecimal("299000"),
                                        "VND",
                                        mentor,
                                        new String[][] {
                                                        { "Giới thiệu về Java",
                                                                        "Tìm hiểu về lịch sử Java, cài đặt JDK và viết chương trình đầu tiên" },
                                                        { "Cú pháp cơ bản và Kiểu dữ liệu",
                                                                        "Học về biến, kiểu dữ liệu, toán tử và cấu trúc điều khiển" },
                                                        { "Lập trình Hướng đối tượng",
                                                                        "Hiểu về classes, objects, inheritance và polymorphism" },
                                                        { "Collections và Streams",
                                                                        "Làm việc với List, Set, Map và Stream API" },
                                                        { "Exception Handling", "Xử lý lỗi và ngoại lệ trong Java" }
                                        });

                        // Course 2: Web Development with Spring Boot
                        createCourseIfNotExists(
                                        "Phát triển Web với Spring Boot",
                                        "Xây dựng ứng dụng web hiện đại với Spring Boot, REST API, và microservices. Thực hành với các dự án thực tế.",
                                        "INTERMEDIATE",
                                        CourseStatus.PUBLIC,
                                        new BigDecimal("599000"),
                                        "VND",
                                        mentor,
                                        new String[][] {
                                                        { "Spring Boot Basics",
                                                                        "Tìm hiểu về Spring Framework và tạo ứng dụng Spring Boot đầu tiên" },
                                                        { "RESTful API Development",
                                                                        "Thiết kế và xây dựng REST API với Spring MVC" },
                                                        { "Database Integration",
                                                                        "Làm việc với JPA, Hibernate và Spring Data" },
                                                        { "Security và Authentication",
                                                                        "Triển khai bảo mật với Spring Security và JWT" },
                                                        { "Microservices Architecture",
                                                                        "Xây dựng và deploy microservices với Spring Cloud" }
                                        });

                        // Course 3: Frontend Development với React
                        createCourseIfNotExists(
                                        "Phát triển Frontend với React",
                                        "Khóa học toàn diện về React.js để xây dựng giao diện người dùng hiện đại, tương tác. Học React hooks, state management, và best practices.",
                                        "INTERMEDIATE",
                                        CourseStatus.PUBLIC,
                                        new BigDecimal("499000"),
                                        "VND",
                                        mentor,
                                        new String[][] {
                                                        { "React Fundamentals",
                                                                        "Components, JSX, Props và State cơ bản" },
                                                        { "React Hooks", "useState, useEffect, useContext và custom hooks" },
                                                        { "State Management", "Context API, Redux và Zustand" },
                                                        { "Routing và Navigation", "React Router và dynamic routing" },
                                                        { "Performance Optimization",
                                                                        "Lazy loading, memoization và code splitting" }
                                        });

                        log.info("🎉 All sample courses initialized successfully");

                } catch (Exception e) {
                        log.error("❌ Error initializing courses: {}", e.getMessage(), e);
                        throw new RuntimeException("Failed to initialize courses", e);
                }
        }

        private void cleanupDeprecatedMockCourses() {
                deleteCourseByTitleIfExists("Thiết kế Database và SQL");
                deleteCourseByTitleIfExists("DevOps và CI/CD Pipeline");
        }

        private void deleteCourseByTitleIfExists(String title) {
                courseRepository.findByTitle(title).ifPresent(course -> {
                        try {
                                courseRepository.delete(course);
                                log.info("🧹 Removed deprecated mock course: {}", title);
                        } catch (Exception ex) {
                                log.warn("⚠️ Cannot remove deprecated mock course '{}': {}", title, ex.getMessage());
                        }
                });
        }

        private void createCourseIfNotExists(String title, String description, String level,
                        CourseStatus status, BigDecimal price, String currency, User author, String[][] modulesData) {

                if (courseRepository.findByTitle(title).isPresent()) {
                        log.info("✅ Course already exists: {}", title);
                        return;
                }

                Instant now = Instant.now();

                Course course = Course.builder()
                                .title(title)
                                .description(description)
                                .level(level)
                                .status(status)
                                .price(price)
                                .currency(currency)
                                .author(author)
                                .createdAt(now)
                                .updatedAt(now)
                                .submittedAt(status == CourseStatus.PENDING || status == CourseStatus.PUBLIC
                                                ? now.minusSeconds(86400)
                                                : null) // 1 day ago if pending/public
                                .publishedAt(status == CourseStatus.PUBLIC ? now : null)
                                .build();

                course = courseRepository.save(course);
                log.info("✅ Created course: {}", title);

                // Create modules with lessons, quizzes and assignments
                for (int i = 0; i < modulesData.length; i++) {
                        String moduleTitle = modulesData[i][0];
                        String moduleDesc = modulesData[i][1];

                        Module module = Module.builder()
                                        .course(course)
                                        .title(moduleTitle)
                                        .description(moduleDesc)
                                        .orderIndex(i + 1)
                                        .createdAt(now)
                                        .updatedAt(now)
                                        .build();

                        module = moduleRepository.save(module);
                        log.info("  ✅ Created module {}: {}", i + 1, moduleTitle);

                        // Create 3 lessons per module
                        createLessonsForModule(module, i + 1);

                        // Create 1 quiz per module
                        createQuizForModule(module, i + 1);

                        // Create 1 assignment per module
                        createAssignmentForModule(module, i + 1);
                }
        }

        private void createLessonsForModule(Module module,
                        int moduleIndex) {
                Instant now = Instant.now();

                String[] lessonTitles = {
                                "Bài giảng video",
                                "Tài liệu đọc",
                                "Thực hành"
                };

                LessonType[] lessonTypes = {
                                LessonType.VIDEO,
                                LessonType.READING,
                                LessonType.CODELAB
                };

                String[] lessonContents = {
                                "Video bài giảng chi tiết với ví dụ thực tế và demo code. Thời lượng 30-45 phút.",
                                "Tài liệu đọc bổ sung với các khái niệm chi tiết, best practices và tips. Khoảng 15-20 trang.",
                                "Bài thực hành với code starter và hướng dẫn chi tiết. Thời gian hoàn thành: 1-2 giờ."
                };

                for (int i = 0; i < 3; i++) {
                        Lesson lesson = Lesson.builder()
                                        .module(module)
                                        .title(lessonTitles[i] + " - Module " + moduleIndex)
                                        .type(lessonTypes[i])
                                        .contentText(lessonContents[i])
                                        .orderIndex(i + 1)
                                        .durationSec(lessonTypes[i] == LessonType.VIDEO ? 2400
                                                        : (lessonTypes[i] == LessonType.READING ? 1200 : 5400)) // in
                                                                                                                // seconds
                                        .videoUrl(lessonTypes[i] == LessonType.VIDEO
                                                        ? "https://example.com/video" + (i + 1)
                                                        : null)
                                        .createdAt(now)
                                        .updatedAt(now)
                                        .build();

                        lessonRepository.save(lesson);
                }
        }

        private void createQuizForModule(Module module,
                        int moduleIndex) {
                Instant now = Instant.now();

                Quiz quiz = Quiz.builder()
                                .module(module)
                                .title("Kiểm tra kiến thức Module " + moduleIndex)
                                .description("Bài kiểm tra trắc nghiệm với 10 câu hỏi để đánh giá kiến thức của bạn")
                                .passScore(70)
                                .createdAt(now)
                                .updatedAt(now)
                                .build();

                quizRepository.save(quiz);
        }

        private void createAssignmentForModule(Module module,
                        int moduleIndex) {
                Instant now = Instant.now();

                Assignment assignment = Assignment.builder()
                                .module(module)
                                .title("Bài tập lớn Module " + moduleIndex)
                                .description("Bài tập thực hành lớn để áp dụng các kiến thức đã học. Yêu cầu hoàn thành project nhỏ và submit code.")
                                .submissionType(SubmissionType.FILE)
                                .maxScore(new BigDecimal("100"))
                                .dueAt(now.plusSeconds(604800)) // Due in 7 days
                                .createdAt(now)
                                .updatedAt(now)
                                .build();

                assignmentRepository.save(assignment);
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
                        try {
                                userProfileService.createCompleteProfile(user.getId(), fullName, null, null, null, null,
                                                null, null, null);
                                log.info("✅ Created basic profile for: {}", email);
                        } catch (Exception e) {
                                log.warn("⚠️ Failed to create profile for {}: {}", email, e.getMessage());
                        }
                } else {
                        log.info("✅ Google student already exists: {}", email);
                }
        }
}
