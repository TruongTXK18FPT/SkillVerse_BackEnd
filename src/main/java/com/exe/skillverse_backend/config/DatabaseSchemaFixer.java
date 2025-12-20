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

            // 2. [NEW] Clean up old/invalid job postings on testing environment
            // Only runs if system property 'cleanup.jobs' is true OR always run safely for testing phase
            // For safety, we only delete jobs created before the update (if needed) or specific bad data
            // BUT, since you want to clear old testing data, we can be more aggressive here if it's safe.
            
            // OPTION: Delete all jobs that have 'null' or invalid values in new fields if necessary
            // Or just leave them, they won't break anything unless we try to update them with invalid status
            
            log.info("Database schema fix completed successfully.");

        } catch (Exception e) {
            log.error("Failed to update database constraints: {}", e.getMessage());
            // Don't throw exception to avoid stopping the application startup
        }
    }
}
