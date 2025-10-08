-- Init script to ensure premium_plans table has correct constraints
-- This should be run before application starts

-- Drop existing constraint if it exists
DO $$ 
BEGIN
    IF EXISTS (
        SELECT 1 
        FROM information_schema.table_constraints 
        WHERE constraint_name = 'premium_plans_plan_type_check' 
        AND table_name = 'premium_plans'
    ) THEN
        EXECUTE 'ALTER TABLE premium_plans DROP CONSTRAINT premium_plans_plan_type_check';
    END IF;
END $$;

-- Add corrected constraint with all enum values
DO $$ 
BEGIN
    IF EXISTS (
        SELECT 1 
        FROM information_schema.tables 
        WHERE table_name = 'premium_plans'
    ) THEN
        EXECUTE 'ALTER TABLE premium_plans 
                 ADD CONSTRAINT premium_plans_plan_type_check 
                 CHECK (plan_type IN (''FREE_TIER'', ''PREMIUM_BASIC'', ''PREMIUM_PLUS'', ''STUDENT_PACK''))';
    END IF;
END $$;
