package com.exe.skillverse_backend.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSchemaFixer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void fixDatabaseConstraints() {
        try {
            log.info("Checking and fixing database constraints...");

            // 1. Fix job_postings status constraint
            String fixJobStatusSql = """
                        DO $$
                        BEGIN
                            -- Drop existing constraint if exists
                            IF EXISTS (
                                SELECT 1 FROM information_schema.table_constraints
                                WHERE constraint_name = 'job_postings_status_check' AND table_name = 'job_postings'
                            ) THEN
                                ALTER TABLE job_postings DROP CONSTRAINT job_postings_status_check;
                            END IF;

                            -- Add updated constraint
                            ALTER TABLE job_postings
                            ADD CONSTRAINT job_postings_status_check
                            CHECK (status IN ('IN_PROGRESS', 'PENDING_APPROVAL', 'OPEN', 'REJECTED', 'CLOSED'));
                        END $$;
                    """;

            jdbcTemplate.execute(fixJobStatusSql);
            log.info("Successfully updated job_postings_status_check constraint.");

            // 2. Fix wallet_transactions transaction_type constraint to include
            // SEMINAR_PAYOUT
            String fixWalletTransactionTypeSql = """
                        DO $$
                        BEGIN
                            -- Drop existing constraint if exists
                            IF EXISTS (
                                SELECT 1 FROM information_schema.table_constraints
                                WHERE constraint_name = 'wallet_transactions_transaction_type_check'
                                AND table_name = 'wallet_transactions'
                            ) THEN
                                ALTER TABLE wallet_transactions DROP CONSTRAINT wallet_transactions_transaction_type_check;
                            END IF;

                            -- Add updated constraint with all TransactionType enum values
                            ALTER TABLE wallet_transactions
                            ADD CONSTRAINT wallet_transactions_transaction_type_check
                            CHECK (transaction_type IN (
                                'DEPOSIT_CASH', 'WITHDRAWAL_CASH', 'PURCHASE_COINS', 'REFUND_CASH',
                                'MENTOR_BOOKING', 'SEMINAR_PURCHASE', 'SEMINAR_PAYOUT',
                                'EARN_COINS', 'SPEND_COINS', 'PURCHASE_COURSE', 'PURCHASE_PREMIUM',
                                'TIP_MENTOR', 'RECEIVE_TIP', 'BONUS_COINS', 'REWARD_ACHIEVEMENT',
                                'DAILY_LOGIN_BONUS', 'REFUND_COINS',
                                'ADMIN_ADJUSTMENT', 'SYSTEM_CORRECTION'
                            ));
                        END $$;
                    """;

            jdbcTemplate.execute(fixWalletTransactionTypeSql);
            log.info("Successfully updated wallet_transactions_transaction_type_check constraint.");

            // 3. Create indexes for seminar analytics queries
            String createSeminarAnalyticsIndexesSql = """
                        DO $$
                        BEGIN
                            -- Index for seminar status queries (analytics by creator)
                            IF NOT EXISTS (
                                SELECT 1 FROM pg_indexes
                                WHERE indexname = 'idx_seminars_creator_status'
                            ) THEN
                                CREATE INDEX idx_seminars_creator_status
                                ON seminars(creator_id, status);
                            END IF;

                            -- Index for ticket count aggregation
                            IF NOT EXISTS (
                                SELECT 1 FROM pg_indexes
                                WHERE indexname = 'idx_seminar_tickets_seminar_id'
                            ) THEN
                                CREATE INDEX idx_seminar_tickets_seminar_id
                                ON seminar_tickets(seminar_id);
                            END IF;
                        END $$;
                    """;

            jdbcTemplate.execute(createSeminarAnalyticsIndexesSql);
            log.info("✅ Seminar analytics indexes verified/created successfully.");

            // 4. Drop unique constraint on quizzes.module_id to allow multiple quizzes per
            // module
            String dropQuizModuleUniqueConstraintSql = """
                        DO $$
                        BEGIN
                            -- Try to find and drop the unique constraint on module_id in quizzes table
                            DECLARE
                                r RECORD;
                            BEGIN
                                FOR r IN (
                                    SELECT tc.constraint_name
                                    FROM information_schema.table_constraints tc
                                    JOIN information_schema.key_column_usage kcu
                                      ON tc.constraint_name = kcu.constraint_name
                                      AND tc.table_schema = kcu.table_schema
                                    WHERE tc.table_name = 'quizzes'
                                      AND tc.constraint_type = 'UNIQUE'
                                      AND kcu.column_name = 'module_id'
                                ) LOOP
                                    EXECUTE 'ALTER TABLE quizzes DROP CONSTRAINT ' || quote_ident(r.constraint_name);
                                    RAISE NOTICE 'Dropped constraint %', r.constraint_name;
                                END LOOP;
                            END;
                        END $$;
                    """;

            jdbcTemplate.execute(dropQuizModuleUniqueConstraintSql);
            log.info("✅ Verified/Dropped unique constraint on quizzes.module_id.");

            // 5. Add version tracking columns to assignment_submissions table
            String addAssignmentVersionTrackingSql = """
                        DO $$
                        BEGIN
                            -- Add new columns if they don't exist
                            IF NOT EXISTS (
                                SELECT 1 FROM information_schema.columns
                                WHERE table_name = 'assignment_submissions' AND column_name = 'attempt_number'
                            ) THEN
                                ALTER TABLE assignment_submissions 
                                ADD COLUMN attempt_number INT NOT NULL DEFAULT 1;
                            END IF;

                            IF NOT EXISTS (
                                SELECT 1 FROM information_schema.columns
                                WHERE table_name = 'assignment_submissions' AND column_name = 'is_newest'
                            ) THEN
                                ALTER TABLE assignment_submissions 
                                ADD COLUMN is_newest BOOLEAN NOT NULL DEFAULT true;
                            END IF;

                            IF NOT EXISTS (
                                SELECT 1 FROM information_schema.columns
                                WHERE table_name = 'assignment_submissions' AND column_name = 'is_previous'
                            ) THEN
                                ALTER TABLE assignment_submissions 
                                ADD COLUMN is_previous BOOLEAN NOT NULL DEFAULT false;
                            END IF;

                            IF NOT EXISTS (
                                SELECT 1 FROM information_schema.columns
                                WHERE table_name = 'assignment_submissions' AND column_name = 'is_late'
                            ) THEN
                                ALTER TABLE assignment_submissions 
                                ADD COLUMN is_late BOOLEAN NOT NULL DEFAULT false;
                            END IF;

                            IF NOT EXISTS (
                                SELECT 1 FROM information_schema.columns
                                WHERE table_name = 'assignment_submissions' AND column_name = 'graded_at'
                            ) THEN
                                ALTER TABLE assignment_submissions 
                                ADD COLUMN graded_at TIMESTAMP;
                            END IF;

                            -- Create indexes for common query patterns
                            IF NOT EXISTS (
                                SELECT 1 FROM pg_indexes
                                WHERE indexname = 'idx_assignment_submissions_assignment_user_attempt'
                            ) THEN
                                CREATE INDEX idx_assignment_submissions_assignment_user_attempt 
                                ON assignment_submissions(assignment_id, user_id, attempt_number DESC);
                            END IF;

                            IF NOT EXISTS (
                                SELECT 1 FROM pg_indexes
                                WHERE indexname = 'idx_assignment_submissions_newest'
                            ) THEN
                                CREATE INDEX idx_assignment_submissions_newest 
                                ON assignment_submissions(assignment_id, is_newest) WHERE is_newest = true;
                            END IF;

                            IF NOT EXISTS (
                                SELECT 1 FROM pg_indexes
                                WHERE indexname = 'idx_assignment_submissions_pending_grading'
                            ) THEN
                                CREATE INDEX idx_assignment_submissions_pending_grading
                                ON assignment_submissions(assignment_id, score) WHERE score IS NULL AND is_newest = true;
                            END IF;

                            -- Update existing submissions if columns were just added
                            UPDATE assignment_submissions 
                            SET attempt_number = 1, is_newest = true, is_previous = false, is_late = false
                            WHERE attempt_number = 1 AND is_newest = false;

                            -- Set is_late for existing submissions that were submitted after due date
                            UPDATE assignment_submissions AS s
                            SET is_late = true
                            FROM assignments AS a
                            WHERE s.assignment_id = a.id 
                              AND a.due_at IS NOT NULL 
                              AND s.submitted_at > a.due_at
                              AND s.is_late = false;

                        END $$;
                    """;

            jdbcTemplate.execute(addAssignmentVersionTrackingSql);
            log.info("✅ Assignment submissions version tracking schema verified/created successfully.");

            // 6. Add order_index column to assignments table
            String addAssignmentOrderIndexSql = """
                        DO $$
                        BEGIN
                            -- Check if order_index column exists
                            IF NOT EXISTS (
                                SELECT 1 FROM information_schema.columns 
                                WHERE table_name = 'assignments' 
                                AND column_name = 'order_index'
                            ) THEN
                                -- Add order_index column
                                ALTER TABLE assignments 
                                ADD COLUMN order_index INTEGER;
                                
                                -- Set default values based on creation date
                                WITH numbered AS (
                                    SELECT id, ROW_NUMBER() OVER (PARTITION BY module_id ORDER BY created_at) as rn
                                    FROM assignments
                                )
                                UPDATE assignments a
                                SET order_index = n.rn - 1
                                FROM numbered n
                                WHERE a.id = n.id;
                                
                                -- Create index for better query performance
                                IF NOT EXISTS (
                                    SELECT 1 FROM pg_indexes
                                    WHERE indexname = 'idx_assignments_order_index'
                                ) THEN
                                    CREATE INDEX idx_assignments_order_index ON assignments(module_id, order_index);
                                END IF;
                                
                                RAISE NOTICE 'Column order_index added to assignments table successfully';
                            ELSE
                                RAISE NOTICE 'Column order_index already exists in assignments table';
                            END IF;
                        END $$;
                    """;

            jdbcTemplate.execute(addAssignmentOrderIndexSql);
            log.info("✅ Assignment order_index column verified/created successfully.");

            // 7. Add learning outcome and grading criteria columns to assignments table
            String addAssignmentMetadataColumnsSql = """
                        DO $$
                        BEGIN
                            -- Add is_required column if not exists
                            IF NOT EXISTS (
                                SELECT 1 FROM information_schema.columns 
                                WHERE table_name = 'assignments' 
                                AND column_name = 'is_required'
                            ) THEN
                                ALTER TABLE assignments 
                                ADD COLUMN is_required BOOLEAN DEFAULT true NOT NULL;
                                
                                RAISE NOTICE 'Column is_required added to assignments table';
                            END IF;
                            
                            -- Add learning_outcome column if not exists
                            IF NOT EXISTS (
                                SELECT 1 FROM information_schema.columns 
                                WHERE table_name = 'assignments' 
                                AND column_name = 'learning_outcome'
                            ) THEN
                                ALTER TABLE assignments 
                                ADD COLUMN learning_outcome TEXT;
                                
                                RAISE NOTICE 'Column learning_outcome added to assignments table';
                            END IF;
                            
                            -- Add grading_criteria column if not exists
                            IF NOT EXISTS (
                                SELECT 1 FROM information_schema.columns 
                                WHERE table_name = 'assignments' 
                                AND column_name = 'grading_criteria'
                            ) THEN
                                ALTER TABLE assignments 
                                ADD COLUMN grading_criteria TEXT;
                                
                                RAISE NOTICE 'Column grading_criteria added to assignments table';
                            END IF;
                        END $$;
                    """;

            jdbcTemplate.execute(addAssignmentMetadataColumnsSql);
            log.info("✅ Assignment metadata columns (is_required, learning_outcome, grading_criteria) verified/created successfully.");

            // 8. Fix notifications type constraint to include assignment-related types
            String fixNotificationTypeConstraintSql = """
                        DO $$
                        BEGIN
                            -- Drop existing constraint if exists
                            IF EXISTS (
                                SELECT 1 FROM information_schema.table_constraints
                                WHERE constraint_name = 'notifications_type_check' AND table_name = 'notifications'
                            ) THEN
                                ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
                            END IF;

                            -- Add updated constraint with all notification types including assignment types
                            ALTER TABLE notifications
                            ADD CONSTRAINT notifications_type_check
                            CHECK (type IN (
                                'LIKE', 'COMMENT', 'PREMIUM_PURCHASE', 'WALLET_DEPOSIT', 'COIN_PURCHASE',
                                'WELCOME', 'PREMIUM_EXPIRATION', 'PREMIUM_CANCEL', 'SYSTEM', 'WARNING',
                                'VIOLATION_REPORT', 'BOOKING_CREATED', 'BOOKING_CONFIRMED', 'BOOKING_REJECTED',
                                'BOOKING_REMINDER', 'BOOKING_COMPLETED', 'BOOKING_CANCELLED', 'BOOKING_REFUND',
                                'PRECHAT_MESSAGE', 'MENTOR_REVIEW_RECEIVED', 'WITHDRAWAL_APPROVED', 'WITHDRAWAL_REJECTED',
                                'MENTOR_LEVEL_UP', 'MENTOR_BADGE_AWARDED', 'TASK_DEADLINE', 'TASK_OVERDUE', 'TASK_REVIEW',
                                'ASSIGNMENT_SUBMITTED', 'ASSIGNMENT_GRADED', 'ASSIGNMENT_LATE'
                            ));
                            
                            RAISE NOTICE 'Notification type constraint updated successfully';
                        END $$;
                    """;

            jdbcTemplate.execute(fixNotificationTypeConstraintSql);
            log.info("✅ Notifications type constraint updated with assignment types successfully.");

            // 9. Add grading rubric system (assignment_criteria and submission_criteria_scores)
            String addGradingRubricSystemSql = """
                        DO $$
                        BEGIN
                            -- Add passing_score column to assignments table
                            IF NOT EXISTS (
                                SELECT 1 FROM information_schema.columns 
                                WHERE table_name = 'assignments' 
                                AND column_name = 'passing_score'
                            ) THEN
                                ALTER TABLE assignments 
                                ADD COLUMN passing_score NUMERIC(5, 2);
                                
                                -- Set default passing score to 70% of max score for existing assignments
                                UPDATE assignments 
                                SET passing_score = max_score * 0.7 
                                WHERE passing_score IS NULL;
                                
                                RAISE NOTICE 'Column passing_score added to assignments table';
                            END IF;

                            -- Create assignment_criteria table
                            IF NOT EXISTS (
                                SELECT 1 FROM information_schema.tables 
                                WHERE table_name = 'assignment_criteria'
                            ) THEN
                                CREATE TABLE assignment_criteria (
                                    id BIGSERIAL PRIMARY KEY,
                                    assignment_id BIGINT NOT NULL,
                                    name VARCHAR(255) NOT NULL,
                                    description TEXT,
                                    max_points NUMERIC(10, 2) NOT NULL,
                                    order_index INTEGER,
                                    is_required BOOLEAN DEFAULT FALSE,
                                    CONSTRAINT fk_assignment_criteria_assignment 
                                        FOREIGN KEY (assignment_id) REFERENCES assignments(id) ON DELETE CASCADE
                                );
                                
                                CREATE INDEX idx_assignment_criteria_assignment_id 
                                    ON assignment_criteria(assignment_id);
                                CREATE INDEX idx_assignment_criteria_order 
                                    ON assignment_criteria(assignment_id, order_index);
                                
                                RAISE NOTICE 'Table assignment_criteria created successfully';
                            END IF;

                            -- Create submission_criteria_scores table
                            IF NOT EXISTS (
                                SELECT 1 FROM information_schema.tables 
                                WHERE table_name = 'submission_criteria_scores'
                            ) THEN
                                CREATE TABLE submission_criteria_scores (
                                    id BIGSERIAL PRIMARY KEY,
                                    submission_id BIGINT NOT NULL,
                                    criteria_id BIGINT NOT NULL,
                                    score NUMERIC(10, 2) NOT NULL,
                                    feedback TEXT,
                                    CONSTRAINT fk_submission_criteria_score_submission 
                                        FOREIGN KEY (submission_id) REFERENCES assignment_submissions(id) ON DELETE CASCADE,
                                    CONSTRAINT fk_submission_criteria_score_criteria 
                                        FOREIGN KEY (criteria_id) REFERENCES assignment_criteria(id) ON DELETE CASCADE,
                                    CONSTRAINT unique_submission_criteria UNIQUE (submission_id, criteria_id)
                                );
                                
                                CREATE INDEX idx_submission_criteria_scores_submission 
                                    ON submission_criteria_scores(submission_id);
                                CREATE INDEX idx_submission_criteria_scores_criteria 
                                    ON submission_criteria_scores(criteria_id);
                                
                                RAISE NOTICE 'Table submission_criteria_scores created successfully';
                            END IF;
                        END $$;
                    """;

            jdbcTemplate.execute(addGradingRubricSystemSql);
            log.info("✅ Grading rubric system (criteria tables) verified/created successfully.");

            log.info("Database schema fix completed successfully.");

        } catch (Exception e) {
            log.error("Failed to update database constraints: {}", e.getMessage());
            // Don't throw exception to avoid stopping the application startup
        }
    }
}
