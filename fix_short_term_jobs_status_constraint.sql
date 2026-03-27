-- Fix short_term_jobs table status check constraint
-- to include all SLA-aware enum values

DO $$
BEGIN
    ALTER TABLE short_term_jobs
    ALTER COLUMN status TYPE VARCHAR(30);

    -- Drop the existing constraint if it exists
    IF EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_name = 'short_term_jobs_status_check'
        AND table_name = 'short_term_jobs'
    ) THEN
        ALTER TABLE short_term_jobs DROP CONSTRAINT short_term_jobs_status_check;
    END IF;

    -- Add the new constraint with all enum values
    ALTER TABLE short_term_jobs ADD CONSTRAINT short_term_jobs_status_check
    CHECK (status IN (
        'DRAFT',
        'PENDING_APPROVAL',
        'PUBLISHED',
        'APPLIED',
        'IN_PROGRESS',
        'SUBMITTED',
        'UNDER_REVIEW',
        'AUTO_APPROVED',
        'CANCELLATION_REQUESTED',
        'AUTO_CANCELLED',
        'DISPUTED',
        'ESCALATED',
        'APPROVED',
        'REJECTED',
        'COMPLETED',
        'PAID',
        'CANCELLED',
        'CLOSED'
    ));

    RAISE NOTICE 'Successfully updated short_term_jobs_status_check constraint';
END $$;
