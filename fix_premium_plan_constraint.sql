-- Fix premium_plans plan_type check constraint to include all current plan types
-- This script keeps FREE_TIER and RECRUITER_PRO available for recruiter subscriptions

DO $$ 
BEGIN
    -- Drop the existing constraint if it exists
    IF EXISTS (
        SELECT 1 
        FROM information_schema.table_constraints 
        WHERE constraint_name = 'premium_plans_plan_type_check' 
        AND table_name = 'premium_plans'
    ) THEN
        ALTER TABLE premium_plans DROP CONSTRAINT premium_plans_plan_type_check;
        RAISE NOTICE 'Dropped existing premium_plans_plan_type_check constraint';
    END IF;
    
    -- Add the corrected constraint with all current plan types
    ALTER TABLE premium_plans 
    ADD CONSTRAINT premium_plans_plan_type_check 
    CHECK (plan_type IN ('FREE_TIER', 'PREMIUM_BASIC', 'PREMIUM_PLUS', 'STUDENT_PACK', 'RECRUITER_PRO'));
    
    RAISE NOTICE 'Added premium_plans_plan_type_check constraint with recruiter support';
    
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'Error occurred: %', SQLERRM;
        RAISE;
END $$;
