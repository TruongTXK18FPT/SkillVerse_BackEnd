-- Fix mentor_bookings table status check constraint
-- to include the current booking workflow statuses

ALTER TABLE mentor_bookings
ALTER COLUMN status TYPE VARCHAR(30);

ALTER TABLE mentor_bookings
ADD COLUMN IF NOT EXISTS learner_completed_at TIMESTAMP;

DO $$
DECLARE status_constraint_name TEXT;
BEGIN
    FOR status_constraint_name IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE n.nspname = current_schema()
          AND t.relname = 'mentor_bookings'
          AND c.contype = 'c'
          AND pg_get_constraintdef(c.oid) ILIKE '%status%'
    LOOP
        EXECUTE format(
            'ALTER TABLE mentor_bookings DROP CONSTRAINT IF EXISTS %I',
            status_constraint_name
        );
    END LOOP;

    UPDATE mentor_bookings
    SET status = 'PENDING_COMPLETION'
    WHERE status = 'MENTOR_COMPLETED';

    ALTER TABLE mentor_bookings ADD CONSTRAINT mentor_bookings_status_check CHECK (
        status IN (
            'PENDING',
            'CONFIRMED',
            'REJECTED',
            'ONGOING',
            'PENDING_COMPLETION',
            'COMPLETED',
            'CANCELLED',
            'DISPUTED',
            'REFUNDED'
        )
    );

    RAISE NOTICE 'Successfully updated mentor_bookings_status_check constraint';
END $$;
