-- Fix short_term_job_applications table status check constraint
-- to include all SLA-aware enum values

DO $$
BEGIN
    -- Drop the existing constraint if it exists
    IF EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_name = 'short_term_job_applications_status_check'
        AND table_name = 'short_term_job_applications'
    ) THEN
        ALTER TABLE short_term_job_applications DROP CONSTRAINT short_term_job_applications_status_check;
    END IF;

    -- Add the new constraint with all enum values
    ALTER TABLE short_term_job_applications ADD CONSTRAINT short_term_job_applications_status_check
    CHECK (status IN (
        'PENDING',
        'ACCEPTED',
        'REJECTED',
        'WORKING',
        'SUBMITTED',
        'SUBMITTED_OVERDUE',
        'REVISION_REQUIRED',
        'REVISION_RESPONSE_OVERDUE',
        'CANCELLATION_REQUESTED',
        'AUTO_CANCELLED',
        'APPROVED',
        'COMPLETED',
        'DISPUTE_OPENED',
        'CANCELLED',
        'WITHDRAWN'
    ));

    RAISE NOTICE 'Successfully updated short_term_job_applications_status_check constraint';
END $$;

-- Fix urgency column width: VERY_URGENT is 12 chars > default varchar(10)
ALTER TABLE short_term_jobs ALTER COLUMN urgency TYPE VARCHAR(20);

RAISE NOTICE 'Successfully fixed urgency column width';
