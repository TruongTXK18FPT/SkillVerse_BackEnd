-- =============================================
-- Premium Recruiter - Job Boost Migration
-- Creates job_boosts table for premium job boosting feature
-- =============================================

-- Job Boost table - stores boost information for premium jobs
CREATE TABLE job_boosts (
    id BIGSERIAL PRIMARY KEY,
    job_posting_id BIGINT NOT NULL REFERENCES job_postings(id) ON DELETE CASCADE,
    recruiter_id BIGINT NOT NULL,

    -- Boost configuration
    boost_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, EXPIRED, SCHEDULED, CANCELLED
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    scheduled_start_at TIMESTAMP, -- For scheduled boosts

    -- Analytics
    impressions INTEGER DEFAULT 0, -- How many times the job was viewed in boosted position
    clicks INTEGER DEFAULT 0, -- How many times users clicked on boosted job
    applications INTEGER DEFAULT 0, -- Applications received during boost period

    -- Metadata
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL,

    -- Constraints
    CONSTRAINT uk_job_boost_job UNIQUE (job_posting_id),
    CONSTRAINT chk_boost_dates CHECK (expires_at > started_at)
);

-- Index for efficient queries
CREATE INDEX idx_job_boosts_recruiter ON job_boosts(recruiter_id);
CREATE INDEX idx_job_boosts_status ON job_boosts(boost_status);
CREATE INDEX idx_job_boosts_expires_at ON job_boosts(expires_at);
CREATE INDEX idx_job_boosts_job_posting ON job_boosts(job_posting_id);

-- =============================================
-- Candidate Search Enhancement Tables
-- =============================================

-- Candidate Search Session - tracks recruiter searches
CREATE TABLE candidate_search_sessions (
    id BIGSERIAL PRIMARY KEY,
    recruiter_id BIGINT NOT NULL,
    search_query TEXT,
    filters JSONB, -- Store applied filters

    -- Results
    total_results INTEGER DEFAULT 0,
    page_size INTEGER DEFAULT 20,

    -- Metadata
    searched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500)
);

CREATE INDEX idx_candidate_search_recruiter ON candidate_search_sessions(recruiter_id);
CREATE INDEX idx_candidate_search_date ON candidate_search_sessions(searched_at);

-- Candidate Match Scores - stores pre-calculated match scores for jobs
CREATE TABLE candidate_match_scores (
    id BIGSERIAL PRIMARY KEY,
    job_posting_id BIGINT NOT NULL REFERENCES job_postings(id) ON DELETE CASCADE,
    candidate_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- Scoring components
    skill_match_score DECIMAL(5,4) DEFAULT 0, -- 0-1: skill overlap
    experience_match_score DECIMAL(5,4) DEFAULT 0,
    budget_match_score DECIMAL(5,4) DEFAULT 0,
    premium_bonus_score DECIMAL(5,4) DEFAULT 0, -- Premium candidate bonus
    total_score DECIMAL(5,4) DEFAULT 0, -- Weighted total

    -- AI enhanced (optional)
    ai_fit_summary TEXT, -- AI-generated fit explanation
    ai_skill_signals JSONB, -- AI-extracted skill signals
    ai_reasoning TEXT, -- AI reasoning for match

    -- Metadata
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_recalculated BOOLEAN DEFAULT FALSE,

    CONSTRAINT uk_candidate_match UNIQUE (job_posting_id, candidate_id)
);

CREATE INDEX idx_candidate_match_job ON candidate_match_scores(job_posting_id);
CREATE INDEX idx_candidate_match_candidate ON candidate_match_scores(candidate_id);
CREATE INDEX idx_candidate_match_score ON candidate_match_scores(total_score DESC);

-- Recruiter Shortlists - recruiters can shortlist candidates
CREATE TABLE recruiter_shortlists (
    id BIGSERIAL PRIMARY KEY,
    recruiter_id BIGINT NOT NULL,
    candidate_id BIGINT NOT NULL,
    job_posting_id BIGINT, -- Optional: shortlist for specific job

    notes TEXT,
    shortlist_status VARCHAR(20) DEFAULT 'ACTIVE', -- ACTIVE, HIRED, REJECTED, ARCHIVED

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_recruiter_shortlist UNIQUE (recruiter_id, candidate_id, job_posting_id)
);

CREATE INDEX idx_shortlist_recruiter ON recruiter_shortlists(recruiter_id);
CREATE INDEX idx_shortlist_candidate ON recruiter_shortlists(candidate_id);
CREATE INDEX idx_shortlist_job ON recruiter_shortlists(job_posting_id);

-- Job Application Enhanced Tracking
ALTER TABLE job_applications ADD COLUMN IF NOT EXISTS source_type VARCHAR(30) DEFAULT 'DIRECT'; -- DIRECT, AI_RECOMMENDED, BOOSTED_LIST
ALTER TABLE job_applications ADD COLUMN IF NOT EXISTS impression_id BIGINT; -- Link to impression event

-- =============================================
-- Job Boost Analytics - Event Tracking
-- =============================================

CREATE TABLE job_boost_impressions (
    id BIGSERIAL PRIMARY KEY,
    job_boost_id BIGINT NOT NULL REFERENCES job_boosts(id) ON DELETE CASCADE,
    user_id BIGINT, -- NULL for anonymous

    event_type VARCHAR(20) NOT NULL, -- IMPRESSION, CLICK, SAVE, APPLY
    position_shown INTEGER, -- Position in job list when shown

    metadata JSONB, -- Additional context

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_boost_impression_boost ON job_boost_impressions(job_boost_id);
CREATE INDEX idx_boost_impression_user ON job_boost_impressions(user_id);
CREATE INDEX idx_boost_impression_type ON job_boost_impressions(event_type);
CREATE INDEX idx_boost_impression_date ON job_boost_impressions(created_at);

-- =============================================
-- Update existing premium plan feature limits
-- =============================================

-- Add JOB_BOOST_MONTHLY limits for existing plans (if not exists)
-- This will be handled by FeatureLimitsDataInitializer

COMMENT ON TABLE job_boosts IS 'Stores boost information for premium job postings. Jobs can be boosted to appear at top of listings.';
COMMENT ON TABLE candidate_match_scores IS 'Pre-calculated match scores between jobs and candidates for fast retrieval and ranking';
COMMENT ON TABLE candidate_search_sessions IS 'Tracks recruiter candidate searches for analytics and optimization';
COMMENT ON TABLE recruiter_shortlists IS 'Recruiters can shortlist candidates for future reference or hiring';
