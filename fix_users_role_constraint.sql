-- Fix users table primary_role check constraint to include PARENT role
-- Run this script in your database tool (pgAdmin, DBeaver, etc.)

DO $$ 
BEGIN
    -- Drop the existing constraint if it exists
    IF EXISTS (
        SELECT 1 
        FROM information_schema.table_constraints 
        WHERE constraint_name = 'users_primary_role_check' 
        AND table_name = 'users'
    ) THEN
        ALTER TABLE users DROP CONSTRAINT users_primary_role_check;
    END IF;

    -- Add the new constraint with all enum values including PARENT
    ALTER TABLE users ADD CONSTRAINT users_primary_role_check 
    CHECK (primary_role IN (
        'USER', 
        'MENTOR', 
        'RECRUITER', 
        'PARENT', 
        'ADMIN', 
        'USER_ADMIN', 
        'CONTENT_ADMIN', 
        'COMMUNITY_ADMIN', 
        'FINANCE_ADMIN', 
        'PREMIUM_ADMIN', 
        'AI_ADMIN', 
        'SUPPORT_ADMIN', 
        'SYSTEM_ADMIN'
    ));
    
    RAISE NOTICE 'Updated users_primary_role_check constraint successfully';
END $$;
