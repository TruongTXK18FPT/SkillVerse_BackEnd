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

            log.info("Database schema fix completed successfully.");

        } catch (Exception e) {
            log.error("Failed to update database constraints: {}", e.getMessage());
            // Don't throw exception to avoid stopping the application startup
        }
    }
}
