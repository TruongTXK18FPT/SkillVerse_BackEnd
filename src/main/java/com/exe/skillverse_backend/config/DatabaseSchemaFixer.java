package com.exe.skillverse_backend.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * DatabaseSchemaFixer — idempotent runtime migrations applied on every startup.
 * Only contains patches not yet baked into docker-init.sql.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSchemaFixer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void fixDatabaseConstraints() {
        try {
            log.info("Applying incremental database schema patches...");

            // Patch 1: Course rejection fields
            jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                        WHERE table_name = 'courses' AND column_name = 'rejection_reason') THEN
                        ALTER TABLE courses ADD COLUMN rejection_reason TEXT;
                    END IF;
                    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                        WHERE table_name = 'courses' AND column_name = 'rejected_at') THEN
                        ALTER TABLE courses ADD COLUMN rejected_at TIMESTAMP;
                    END IF;
                END $$;
            """);
            log.info("course rejection columns ok.");

            // Patch 2: Course suspension fields
            jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                        WHERE table_name = 'courses' AND column_name = 'suspension_reason') THEN
                        ALTER TABLE courses ADD COLUMN suspension_reason TEXT;
                    END IF;
                    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                        WHERE table_name = 'courses' AND column_name = 'suspended_at') THEN
                        ALTER TABLE courses ADD COLUMN suspended_at TIMESTAMP;
                    END IF;
                    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                        WHERE table_name = 'courses' AND column_name = 'suspended_by') THEN
                        ALTER TABLE courses ADD COLUMN suspended_by BIGINT;
                    END IF;
                END $$;
            """);
            log.info("course suspension columns ok.");

            // Patch 3: Notification type constraint — add COURSE_REJECTED, COURSE_SUSPENDED, COURSE_RESTORED
            jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF EXISTS (SELECT 1 FROM information_schema.table_constraints
                        WHERE constraint_name = 'notifications_type_check'
                          AND table_name = 'notifications') THEN
                        ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
                    END IF;
                    ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
                    CHECK (type IN (
                        'LIKE','COMMENT','PREMIUM_PURCHASE','WALLET_DEPOSIT','COIN_PURCHASE',
                        'WELCOME','PREMIUM_EXPIRATION','PREMIUM_CANCEL','SYSTEM','WARNING',
                        'VIOLATION_REPORT','BOOKING_CREATED','BOOKING_CONFIRMED','BOOKING_REJECTED',
                        'BOOKING_REMINDER','BOOKING_COMPLETED','BOOKING_CANCELLED','BOOKING_REFUND',
                        'PRECHAT_MESSAGE','MENTOR_REVIEW_RECEIVED','WITHDRAWAL_APPROVED','WITHDRAWAL_REJECTED',
                        'MENTOR_LEVEL_UP','MENTOR_BADGE_AWARDED','TASK_DEADLINE','TASK_OVERDUE','TASK_REVIEW',
                        'ASSIGNMENT_SUBMITTED','ASSIGNMENT_GRADED','ASSIGNMENT_LATE',
                        'COURSE_REJECTED','COURSE_SUSPENDED','COURSE_RESTORED'
                    ));
                END $$;
            """);;
            log.info("notification type constraint updated.");

            // Patch 4: assignment_criteria.passing_points — backfill NULL → 0 and set NOT NULL DEFAULT
            jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF EXISTS (SELECT 1 FROM information_schema.columns
                        WHERE table_name = 'assignment_criteria' AND column_name = 'passing_points') THEN
                        -- Backfill any NULL values to 0
                        UPDATE assignment_criteria SET passing_points = 0 WHERE passing_points IS NULL;
                        -- Set NOT NULL + DEFAULT
                        ALTER TABLE assignment_criteria ALTER COLUMN passing_points SET DEFAULT 0;
                        ALTER TABLE assignment_criteria ALTER COLUMN passing_points SET NOT NULL;
                    END IF;
                END $$;
            """);
            log.info("assignment_criteria.passing_points NOT NULL (default 0) ok.");

            // Patch 5: assignment_submissions.is_passed — persist grading result (NULL = not yet graded)
            jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                        WHERE table_name = 'assignment_submissions' AND column_name = 'is_passed') THEN
                        ALTER TABLE assignment_submissions ADD COLUMN is_passed BOOLEAN DEFAULT NULL;
                    END IF;
                END $$;
            """);
            log.info("assignment_submissions.is_passed column ok.");

            // Patch 6: courses.status CHECK constraint — add REJECTED, SUSPENDED to allowed values
            jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF EXISTS (SELECT 1 FROM information_schema.table_constraints
                        WHERE constraint_name = 'courses_status_check'
                          AND table_name = 'courses') THEN
                        ALTER TABLE courses DROP CONSTRAINT courses_status_check;
                    END IF;
                    ALTER TABLE courses ADD CONSTRAINT courses_status_check
                    CHECK (status IN ('DRAFT', 'PENDING', 'PUBLIC', 'ARCHIVED', 'REJECTED', 'SUSPENDED'));
                END $$;
            """);
            log.info("courses.status CHECK constraint updated (added REJECTED, SUSPENDED).");

            log.info("All schema patches applied successfully.");
        } catch (Exception e) {
            log.error("Failed to apply schema patches: {}", e.getMessage());
        }
    }
}
