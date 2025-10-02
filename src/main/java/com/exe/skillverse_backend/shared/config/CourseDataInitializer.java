package com.exe.skillverse_backend.shared.config;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.Lesson;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import com.exe.skillverse_backend.course_service.entity.enums.LessonType;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.repository.LessonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@Order(2) // Run after DataInitializer
@RequiredArgsConstructor
@Slf4j
public class CourseDataInitializer implements CommandLineRunner {

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Check if we should clear existing data (for development/testing)
        boolean shouldClearData = args.length > 0 && "clear".equals(args[0]);
        
        if (shouldClearData) {
            clearExistingData();
        }
        
        initializeCoursesAndLessons();
    }

    private void initializeCoursesAndLessons() {
        try {
            // Check if courses already exist
            long existingCourseCount = courseRepository.count();
            if (existingCourseCount > 0) {
                log.info("✅ {} courses already exist, skipping initialization", existingCourseCount);
                return;
            }

            // Get mentor user
            User mentorUser = userRepository.findByEmail("exementor@gmail.com")
                    .orElseThrow(() -> new RuntimeException("Mentor user not found"));

            log.info("🚀 Creating sample courses and lessons...");

            // Create sample courses
            createCourse("React Fundamentals", 
                    "Learn the basics of React development from scratch", 
                    "Beginner", CourseStatus.PUBLIC, mentorUser);

            createCourse("Advanced TypeScript", 
                    "Master TypeScript for enterprise applications", 
                    "Advanced", CourseStatus.PENDING, mentorUser);

            createCourse("Spring Boot Mastery", 
                    "Complete guide to Spring Boot development", 
                    "Intermediate", CourseStatus.DRAFT, mentorUser);

            log.info("🎉 All sample courses and lessons initialized successfully");

        } catch (Exception e) {
            log.error("❌ Error initializing courses and lessons: {}", e.getMessage(), e);
            // Don't rethrow the exception to prevent application startup failure
        }
    }

    private void clearExistingData() {
        try {
            log.info("🧹 Clearing existing course data...");
            
            // Delete all lessons first (due to foreign key constraints)
            lessonRepository.deleteAll();
            log.info("✅ Cleared all lessons");
            
            // Delete all courses
            courseRepository.deleteAll();
            log.info("✅ Cleared all courses");
            
            log.info("🎉 Course data cleared successfully");
        } catch (Exception e) {
            log.error("❌ Error clearing course data: {}", e.getMessage(), e);
        }
    }

    private void createCourse(String title, String description, 
                            String level, CourseStatus status, User author) {
        Course course = Course.builder()
                .title(title)
                .description(description)
                .level(level)
                .status(status)
                .author(author)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        course = courseRepository.save(course);
        log.info("✅ Created course: {} (ID: {})", title, course.getId());

        // Create sample lessons for this course
        createLessonsForCourse(course);
    }

    private void createLessonsForCourse(Course course) {
        List<Lesson> lessons = List.of(
            Lesson.builder()
                    .course(course)
                    .title("Introduction to " + course.getTitle())
                    .type(LessonType.VIDEO)
                    .orderIndex(1)
                    .contentText("Welcome to this comprehensive course!")
                    .durationSec(1200) // 20 minutes
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build(),
            
            Lesson.builder()
                    .course(course)
                    .title("Getting Started")
                    .type(LessonType.READING)
                    .orderIndex(2)
                    .contentText("Let's dive into the fundamentals and get you started.")
                    .durationSec(900) // 15 minutes
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build(),
            
            Lesson.builder()
                    .course(course)
                    .title("Hands-on Practice")
                    .type(LessonType.VIDEO)
                    .orderIndex(3)
                    .contentText("Time to put your knowledge into practice!")
                    .durationSec(1800) // 30 minutes
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build(),
            
            Lesson.builder()
                    .course(course)
                    .title("Quiz: Test Your Knowledge")
                    .type(LessonType.QUIZ)
                    .orderIndex(4)
                    .contentText("Test what you've learned so far.")
                    .durationSec(600) // 10 minutes
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build(),
            
            Lesson.builder()
                    .course(course)
                    .title("Assignment: Build Something")
                    .type(LessonType.ASSIGNMENT)
                    .orderIndex(5)
                    .contentText("Apply your skills in a real project.")
                    .durationSec(3600) // 60 minutes
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build()
        );

        for (Lesson lesson : lessons) {
            if (!lessonRepository.existsByCourseIdAndTitleIgnoreCase(course.getId(), lesson.getTitle())) {
                lessonRepository.save(lesson);
                log.info("✅ Created lesson: {} for course: {}", lesson.getTitle(), course.getTitle());
            }
        }
    }
}
