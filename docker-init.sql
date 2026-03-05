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
                 CHECK (plan_type IN (''FREE_TIER'', ''PREMIUM_BASIC'', ''PREMIUM_PLUS'', ''STUDENT_PACK'', ''RECRUITER_PRO''))';
    END IF;
END $$;

-- Fix plan_feature_limits feature_type constraint
DO $$ 
BEGIN
    IF EXISTS (
        SELECT 1 
        FROM information_schema.table_constraints 
        WHERE constraint_name = 'plan_feature_limits_feature_type_check' 
        AND table_name = 'plan_feature_limits'
    ) THEN
        EXECUTE 'ALTER TABLE plan_feature_limits DROP CONSTRAINT plan_feature_limits_feature_type_check';
    END IF;
END $$;

DO $$ 
BEGIN
    IF EXISTS (
        SELECT 1 
        FROM information_schema.tables 
        WHERE table_name = 'plan_feature_limits'
    ) THEN
        EXECUTE 'ALTER TABLE plan_feature_limits 
                 ADD CONSTRAINT plan_feature_limits_feature_type_check 
                 CHECK (feature_type IN (''AI_CHATBOT_REQUESTS'', ''AI_ROADMAP_GENERATION'', ''MENTOR_BOOKING_MONTHLY'', ''COIN_EARNING_MULTIPLIER'', ''PRIORITY_SUPPORT'', ''JOB_POSTING_MONTHLY'', ''HIGHLIGHT_JOB_POST'', ''AI_CANDIDATE_SUGGESTION''))';
    END IF;
END $$;

-- Fix short_term_jobs status constraint to include PENDING_APPROVAL
DO $$ 
BEGIN
    IF EXISTS (
        SELECT 1 
        FROM information_schema.table_constraints 
        WHERE constraint_name = 'short_term_jobs_status_check' 
        AND table_name = 'short_term_jobs'
    ) THEN
        EXECUTE 'ALTER TABLE short_term_jobs DROP CONSTRAINT short_term_jobs_status_check';
    END IF;
END $$;

DO $$ 
BEGIN
    IF EXISTS (
        SELECT 1 
        FROM information_schema.tables 
        WHERE table_name = 'short_term_jobs'
    ) THEN
        EXECUTE 'ALTER TABLE short_term_jobs 
                 ADD CONSTRAINT short_term_jobs_status_check 
                 CHECK (status IN (''DRAFT'', ''PENDING_APPROVAL'', ''PUBLISHED'', ''APPLIED'', ''IN_PROGRESS'', ''SUBMITTED'', ''UNDER_REVIEW'', ''APPROVED'', ''REJECTED'', ''COMPLETED'', ''PAID'', ''CANCELLED'', ''DISPUTED''))';
    END IF;
END $$;
