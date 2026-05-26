-- ============================================================
-- SKILLVERSE DATABASE SCHEMA
-- PostgreSQL DDL Script
-- Generated from JPA Entity classes
-- ============================================================

-- ============================================================
-- SECTION 1: SHARED / AUTH SERVICE (Core Tables)
-- ============================================================

-- Roles table
CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT
);

-- Users table
CREATE TABLE users (
    id                   BIGSERIAL ![1777199275582](image/database_schema/1777199275582.png)PRIMARY KEY,
    email                VARCHAR(255) NOT NULL UNIQUE,
    password             VARCHAR(255),
    first_name           VARCHAR(255),
    last_name            VARCHAR(255),
    phone_number         VARCHAR(50),
    avatar_url           TEXT,
    primary_role         VARCHAR(50) NOT NULL DEFAULT 'USER',
    auth_provider        VARCHAR(50) NOT NULL DEFAULT 'LOCAL',
    google_linked        BOOLEAN NOT NULL DEFAULT FALSE,
    status               VARCHAR(50) NOT NULL DEFAULT 'INACTIVE',
    is_email_verified    BOOLEAN NOT NULL DEFAULT FALSE,
    verification_otp     VARCHAR(10),
    otp_expiry_time      TIMESTAMP,
    otp_attempts         INTEGER NOT NULL DEFAULT 0,
    last_otp_sent_time   TIMESTAMP,
    password_changed_at  TIMESTAMP,
    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_primary_role ON users(primary_role);
CREATE INDEX idx_users_status ON users(status);

-- User roles (many-to-many)
CREATE TABLE user_roles (
    user_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id  BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);

-- Refresh tokens
CREATE TABLE refresh_tokens (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token              VARCHAR(64) NOT NULL UNIQUE,
    created_at         TIMESTAMP,
    device_session_id  VARCHAR(64),
    expiry_date        TIMESTAMP NOT NULL
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_expiry_date ON refresh_tokens(expiry_date);

-- Invalidated tokens (JWT blocklist)
CREATE TABLE invalidated_tokens (
    jti             VARCHAR(36) PRIMARY KEY,
    invalidated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invalidated_tokens_invalidated_at ON invalidated_tokens(invalidated_at);

-- Skills (self-referential hierarchy)
CREATE TABLE skills (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(255) NOT NULL,
    category         VARCHAR(255),
    description      TEXT,
    parent_skill_id  BIGINT REFERENCES skills(id) ON DELETE SET NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_skills_name ON skills(name);
CREATE INDEX idx_skills_category ON skills(category);
CREATE INDEX idx_skills_parent_skill_id ON skills(parent_skill_id);

-- Tags
CREATE TABLE tags (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tags_name ON tags(name);

-- Media
CREATE TABLE media (
    id                         BIGSERIAL PRIMARY KEY,
    url                        TEXT NOT NULL,
    type                       VARCHAR(100) NOT NULL,
    file_name                  VARCHAR(255),
    file_size                  BIGINT,
    uploaded_by                BIGINT REFERENCES users(id) ON DELETE SET NULL,
    uploaded_at                TIMESTAMP NOT NULL DEFAULT NOW(),
    cloudinary_public_id       VARCHAR(500),
    cloudinary_resource_type   VARCHAR(100)
);

CREATE INDEX idx_media_uploaded_by ON media(uploaded_by);
CREATE INDEX idx_media_type ON media(type);
CREATE INDEX idx_media_cloudinary_public_id ON media(cloudinary_public_id);

-- File uploads
CREATE TABLE file_uploads (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_name      VARCHAR(255) NOT NULL,
    file_path      VARCHAR(255) NOT NULL,
    file_type      VARCHAR(255) NOT NULL,
    uploaded_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    related_object VARCHAR(255),
    object_id      BIGINT
);

CREATE INDEX idx_file_uploads_user_id ON file_uploads(user_id);
CREATE INDEX idx_file_uploads_related_object ON file_uploads(related_object);

-- Audit logs
CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users(id) ON DELETE SET NULL,
    action      VARCHAR(255) NOT NULL,
    object_type VARCHAR(255) NOT NULL,
    object_id   BIGINT,
    details     TEXT,
    timestamp   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_timestamp ON audit_logs(timestamp);
CREATE INDEX idx_audit_logs_object_type ON audit_logs(object_type);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);

-- User history
CREATE TABLE user_history (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_type  VARCHAR(255) NOT NULL,
    object_type VARCHAR(255) NOT NULL,
    object_id   BIGINT NOT NULL,
    timestamp   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_history_user_id ON user_history(user_id);
CREATE INDEX idx_user_history_timestamp ON user_history(timestamp);
CREATE INDEX idx_user_history_object_type ON user_history(object_type);

-- ============================================================
-- SECTION 2: USER SERVICE
-- ============================================================

-- User profiles (1:1 with users)
CREATE TABLE user_profiles (
    user_id          BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    full_name        VARCHAR(255),
    avatar_media_id  BIGINT REFERENCES media(id) ON DELETE SET NULL,
    avatar_position  VARCHAR(255),
    bio              TEXT,
    phone            VARCHAR(255),
    address          TEXT,
    region           VARCHAR(255),
    company_id       BIGINT,
    social_links     TEXT,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_profiles_region ON user_profiles(region);
CREATE INDEX idx_user_profiles_company_id ON user_profiles(company_id);

-- User skills (many-to-many with proficiency)
CREATE TABLE user_skills (
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    skill_id     BIGINT NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    proficiency  INTEGER NOT NULL,
    PRIMARY KEY (user_id, skill_id)
);

CREATE INDEX idx_user_skills_user_id ON user_skills(user_id);
CREATE INDEX idx_user_skills_skill_id ON user_skills(skill_id);
CREATE INDEX idx_user_skills_proficiency ON user_skills(proficiency);

-- ============================================================
-- SECTION 3: COURSE SERVICE
-- ============================================================

-- Courses
CREATE TABLE courses (
    id                        BIGSERIAL PRIMARY KEY,
    title                     VARCHAR(200) NOT NULL,
    description               TEXT,
    level                     VARCHAR(50),
    category                  VARCHAR(120),
    short_description         VARCHAR(300),
    estimated_duration_hours  INTEGER,
    language                  VARCHAR(40),
    status                    VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    price                     DECIMAL(12, 2),
    currency                  VARCHAR(10),
    author_id                 BIGINT NOT NULL REFERENCES users(id),
    thumbnail_media_id        BIGINT REFERENCES media(id),
    created_at                TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP,
    submitted_at              TIMESTAMP,
    published_at              TIMESTAMP,
    rejection_reason          TEXT,
    rejected_at               TIMESTAMP,
    suspension_reason         TEXT,
    suspended_at              TIMESTAMP,
    suspended_by              BIGINT,
    active_revision_id        BIGINT,
    latest_revision_id        BIGINT,
    revisioning_enabled       BOOLEAN NOT NULL DEFAULT FALSE,
    upgrade_policy            VARCHAR(32) NOT NULL DEFAULT 'MANUAL'
);

CREATE INDEX idx_courses_author_id ON courses(author_id);
CREATE INDEX idx_courses_status ON courses(status);

-- Course learning objectives (element collection)
CREATE TABLE course_learning_objectives (
    course_id   BIGINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    objective   VARCHAR(255)
);

-- Course requirements (element collection)
CREATE TABLE course_requirements (
    course_id    BIGINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    requirement  VARCHAR(255)
);

-- Modules
CREATE TABLE modules (
    id          BIGSERIAL PRIMARY KEY,
    course_id   BIGINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    title       VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    order_index INTEGER,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP
);

CREATE INDEX idx_modules_course_id ON modules(course_id);
CREATE INDEX idx_modules_order_index ON modules(order_index);

-- Lessons
CREATE TABLE lessons (
    id             BIGSERIAL PRIMARY KEY,
    module_id      BIGINT NOT NULL REFERENCES modules(id) ON DELETE CASCADE,
    title          VARCHAR(200) NOT NULL,
    type           VARCHAR(20) NOT NULL,
    order_index    INTEGER,
    content_text   TEXT,
    resource_url   VARCHAR(500),
    video_url      VARCHAR(500),
    video_media_id BIGINT REFERENCES media(id),
    duration_sec   INTEGER,
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at     TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_lessons_module_id ON lessons(module_id);

-- Lesson attachments
CREATE TABLE lesson_attachments (
    id           BIGSERIAL PRIMARY KEY,
    lesson_id    BIGINT NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    media_id     BIGINT REFERENCES media(id),
    title        VARCHAR(200) NOT NULL,
    description  VARCHAR(500),
    external_url VARCHAR(500),
    type         VARCHAR(20) NOT NULL,
    file_size    BIGINT,
    order_index  INTEGER,
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at   TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_lesson_attachments_lesson_id ON lesson_attachments(lesson_id);
CREATE INDEX idx_lesson_attachments_order ON lesson_attachments(lesson_id, order_index);

-- Quizzes
CREATE TABLE quizzes (
    id                  BIGSERIAL PRIMARY KEY,
    module_id           BIGINT NOT NULL REFERENCES modules(id) ON DELETE CASCADE,
    title               VARCHAR(200),
    description         TEXT,
    pass_score          INTEGER,
    max_attempts        INTEGER,
    time_limit_minutes  INTEGER,
    rounding_increment  INTEGER,
    grading_method      VARCHAR(20),
    is_assessment       BOOLEAN,
    cooldown_hours      INTEGER,
    order_index         INTEGER,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_quizzes_module_id ON quizzes(module_id);

-- Quiz questions
CREATE TABLE quiz_questions (
    id            BIGSERIAL PRIMARY KEY,
    quiz_id       BIGINT NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    question_text TEXT NOT NULL,
    question_type VARCHAR(20) NOT NULL,
    score         INTEGER NOT NULL DEFAULT 1,
    order_index   INTEGER
);

CREATE INDEX idx_quiz_questions_quiz_id ON quiz_questions(quiz_id);

-- Quiz options
CREATE TABLE quiz_options (
    id          BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL REFERENCES quiz_questions(id) ON DELETE CASCADE,
    option_text TEXT NOT NULL,
    is_correct  BOOLEAN NOT NULL,
    feedback    VARCHAR(255),
    order_index INTEGER
);

CREATE INDEX idx_quiz_options_question_id ON quiz_options(question_id);

-- Quiz attempt sessions
CREATE TABLE quiz_attempt_sessions (
    id            BIGSERIAL PRIMARY KEY,
    quiz_id       BIGINT NOT NULL REFERENCES quizzes(id),
    user_id       BIGINT NOT NULL,
    session_token VARCHAR(64) NOT NULL UNIQUE,
    status        VARCHAR(20) NOT NULL,
    started_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_seen_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    submitted_at  TIMESTAMP WITH TIME ZONE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_quiz_attempt_sessions_quiz_id ON quiz_attempt_sessions(quiz_id);
CREATE INDEX idx_quiz_attempt_sessions_user_id ON quiz_attempt_sessions(user_id);
CREATE INDEX idx_quiz_attempt_sessions_status ON quiz_attempt_sessions(status);

-- Quiz attempts
CREATE TABLE quiz_attempts (
    id              BIGSERIAL PRIMARY KEY,
    quiz_id         BIGINT NOT NULL REFERENCES quizzes(id),
    user_id         BIGINT NOT NULL,
    score           INTEGER NOT NULL,
    passed          BOOLEAN NOT NULL,
    correct_answers INTEGER,
    total_questions INTEGER,
    submitted_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_quiz_attempts_quiz_id ON quiz_attempts(quiz_id);
CREATE INDEX idx_quiz_attempts_user_id ON quiz_attempts(user_id);

-- Quiz attempt answer snapshots
CREATE TABLE quiz_attempt_answer_snapshots (
    id                     BIGSERIAL PRIMARY KEY,
    attempt_id             BIGINT NOT NULL REFERENCES quiz_attempts(id) ON DELETE CASCADE,
    question_id            BIGINT NOT NULL,
    question_order_index   INTEGER,
    question_text          TEXT NOT NULL,
    question_type          VARCHAR(20) NOT NULL,
    submitted_answer_text  TEXT,
    correct_answer_text    TEXT,
    submitted_answer_json  JSONB,
    options_snapshot_json JSONB,
    answered               BOOLEAN NOT NULL,
    is_correct             BOOLEAN NOT NULL,
    score_earned           INTEGER NOT NULL,
    max_score              INTEGER NOT NULL
);

CREATE INDEX idx_quiz_attempt_answer_snapshots_attempt_id ON quiz_attempt_answer_snapshots(attempt_id);

-- Assignments
CREATE TABLE assignments (
    id               BIGSERIAL PRIMARY KEY,
    module_id        BIGINT NOT NULL REFERENCES modules(id) ON DELETE CASCADE,
    title            VARCHAR(200) NOT NULL,
    description      TEXT,
    submission_type  VARCHAR(20) NOT NULL,
    max_score        DECIMAL(5, 2) NOT NULL,
    passing_score    DECIMAL(5, 2),
    order_index      INTEGER,
    is_required      BOOLEAN NOT NULL DEFAULT TRUE,
    learning_outcome TEXT,
    grading_criteria TEXT,
    due_at           TIMESTAMP WITH TIME ZONE,
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_assignments_module_id ON assignments(module_id);

-- Assignment criteria
CREATE TABLE assignment_criteria (
    id             BIGSERIAL PRIMARY KEY,
    assignment_id  BIGINT NOT NULL REFERENCES assignments(id) ON DELETE CASCADE,
    name           VARCHAR(255) NOT NULL,
    description    TEXT,
    max_points     DECIMAL(10, 2) NOT NULL,
    passing_points DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    order_index    INTEGER,
    is_required    BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_assignment_criteria_assignment_id ON assignment_criteria(assignment_id);

-- Assignment submissions
CREATE TABLE assignment_submissions (
    id             BIGSERIAL PRIMARY KEY,
    assignment_id  BIGINT NOT NULL REFERENCES assignments(id),
    user_id        BIGINT NOT NULL,
    file_media_id  BIGINT REFERENCES media(id),
    submission_text TEXT,
    link_url       VARCHAR(500),
    submitted_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    score          DECIMAL(5, 2),
    graded_by      BIGINT,
    feedback       TEXT,
    attempt_number INTEGER NOT NULL DEFAULT 1,
    is_newest      BOOLEAN NOT NULL DEFAULT TRUE,
    is_previous    BOOLEAN NOT NULL DEFAULT FALSE,
    is_late        BOOLEAN NOT NULL DEFAULT FALSE,
    graded_at      TIMESTAMP WITH TIME ZONE,
    is_passed      BOOLEAN
);

CREATE INDEX idx_assignment_submissions_assignment_user ON assignment_submissions(assignment_id, user_id);
CREATE INDEX idx_assignment_submissions_user ON assignment_submissions(user_id);
CREATE INDEX idx_assignment_submissions_assignment_new ON assignment_submissions(assignment_id, is_newest);

-- Submission criteria scores
CREATE TABLE submission_criteria_scores (
    id           BIGSERIAL PRIMARY KEY,
    submission_id BIGINT NOT NULL REFERENCES assignment_submissions(id) ON DELETE CASCADE,
    criteria_id  BIGINT NOT NULL REFERENCES assignment_criteria(id),
    score        DECIMAL(10, 2) NOT NULL,
    feedback     TEXT
);

CREATE INDEX idx_submission_criteria_scores_submission_id ON submission_criteria_scores(submission_id);

-- Coding exercises
CREATE TABLE coding_exercises (
    id           BIGSERIAL PRIMARY KEY,
    module_id    BIGINT NOT NULL REFERENCES modules(id) ON DELETE CASCADE UNIQUE,
    title        VARCHAR(200) NOT NULL,
    prompt       TEXT NOT NULL,
    language     VARCHAR(50) NOT NULL,
    starter_code TEXT,
    max_score    DECIMAL(5, 2) NOT NULL
);

-- Coding test cases
CREATE TABLE coding_test_cases (
    id             BIGSERIAL PRIMARY KEY,
    exercise_id    BIGINT NOT NULL REFERENCES coding_exercises(id) ON DELETE CASCADE,
    kind           VARCHAR(10) NOT NULL,
    input          TEXT NOT NULL,
    expected_output TEXT NOT NULL,
    score_weight   DECIMAL(5, 2) NOT NULL,
    order_index    INTEGER
);

CREATE INDEX idx_coding_test_cases_exercise_order ON coding_test_cases(exercise_id, order_index);

-- Coding submissions
CREATE TABLE coding_submissions (
    id             BIGSERIAL PRIMARY KEY,
    exercise_id    BIGINT NOT NULL REFERENCES coding_exercises(id),
    user_id        BIGINT NOT NULL,
    submitted_code TEXT NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    score          DECIMAL(5, 2),
    feedback       TEXT,
    submitted_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_coding_submissions_exercise_user ON coding_submissions(exercise_id, user_id);
CREATE INDEX idx_coding_submissions_status ON coding_submissions(status);

-- Course enrollment
CREATE TABLE course_enrollment (
    user_id                  BIGINT NOT NULL,
    course_id                BIGINT NOT NULL,
    enroll_date             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    status                  VARCHAR(20) NOT NULL DEFAULT 'ENROLLED',
    progress_percent        INTEGER NOT NULL DEFAULT 0,
    entitlement_source      VARCHAR(20) NOT NULL DEFAULT 'PURCHASE',
    entitlement_ref         VARCHAR(64),
    learning_revision_id    BIGINT,
    upgrade_policy_snapshot VARCHAR(32),
    last_upgraded_at        TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (user_id, course_id)
);

CREATE INDEX idx_course_enrollment_course_status ON course_enrollment(course_id, status);
CREATE INDEX idx_course_enrollment_user_course ON course_enrollment(user_id, course_id);

-- Module progress
CREATE TABLE module_progress (
    user_id           BIGINT NOT NULL,
    module_id         BIGINT NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    time_spent_sec   INTEGER NOT NULL DEFAULT 0,
    last_position_sec INTEGER,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, module_id)
);

CREATE INDEX idx_module_progress_user_module ON module_progress(user_id, module_id);

-- Lesson progress
CREATE TABLE lesson_progress (
    user_id       BIGINT NOT NULL,
    lesson_id     BIGINT NOT NULL,
    completed     BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at  TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (user_id, lesson_id)
);

CREATE UNIQUE INDEX idx_lesson_progress_user_lesson ON lesson_progress(user_id, lesson_id);

-- Course purchase
CREATE TABLE course_purchase (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    course_id    BIGINT NOT NULL,
    price        DECIMAL(12, 2) NOT NULL,
    currency     VARCHAR(10) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    purchased_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    coupon_code  VARCHAR(50)
);

CREATE INDEX idx_course_purchase_user_course ON course_purchase(user_id, course_id);
CREATE INDEX idx_course_purchase_status ON course_purchase(status);

-- Course skills
CREATE TABLE course_skill (
    course_id BIGINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    skill_id  BIGINT NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    PRIMARY KEY (course_id, skill_id)
);

-- Course revisions
CREATE TABLE course_revisions (
    id                        BIGSERIAL PRIMARY KEY,
    course_id                 BIGINT NOT NULL,
    revision_number           INTEGER NOT NULL,
    status                    VARCHAR(20) NOT NULL,
    title                     VARCHAR(200) NOT NULL,
    description               TEXT,
    level                     VARCHAR(50),
    category                  VARCHAR(120),
    short_description         VARCHAR(300),
    estimated_duration_hours INTEGER,
    language                  VARCHAR(40),
    price                     DECIMAL(12, 2),
    currency                  VARCHAR(10),
    learning_objectives_json  JSONB NOT NULL,
    requirements_json         JSONB NOT NULL,
    content_snapshot_json     JSONB NOT NULL,
    source_revision_id        BIGINT,
    baseline_snapshot_hash    VARCHAR(64),
    snapshot_hash             VARCHAR(64),
    rejected_snapshot_hash    VARCHAR(64),
    snapshot_version         INTEGER,
    source_course_status      VARCHAR(20) NOT NULL,
    created_by                BIGINT,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                TIMESTAMP WITH TIME ZONE,
    submitted_at              TIMESTAMP WITH TIME ZONE,
    approved_at               TIMESTAMP WITH TIME ZONE,
    rejected_at               TIMESTAMP WITH TIME ZONE,
    rejection_reason          TEXT,
    archived_at               TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_course_revisions_course_revision UNIQUE (course_id, revision_number)
);

CREATE INDEX idx_course_revisions_course_id ON course_revisions(course_id);
CREATE INDEX idx_course_revisions_course_status ON course_revisions(course_id, status);

-- Certificates
CREATE TABLE certificates (
    id                                   BIGSERIAL PRIMARY KEY,
    user_id                              BIGINT NOT NULL,
    course_id                            BIGINT NOT NULL,
    type                                 VARCHAR(20) NOT NULL DEFAULT 'COURSE',
    serial                               VARCHAR(64) NOT NULL UNIQUE,
    recipient_name_snapshot              VARCHAR(255),
    course_title_snapshot                VARCHAR(255),
    instructor_name_snapshot             VARCHAR(255),
    instructor_signature_url_snapshot    VARCHAR(1000),
    issued_at                            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    revoked_at                           TIMESTAMP WITH TIME ZONE,
    criteria                             TEXT,
    revoke_reason                        VARCHAR(120)
);

CREATE INDEX idx_certificates_user_course ON certificates(user_id, course_id);

-- ============================================================
-- SECTION 4: BUSINESS SERVICE
-- ============================================================

-- Recruiter profiles
CREATE TABLE recruiter_profiles (
    user_id                                               BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    company_name                                          VARCHAR(255) NOT NULL,
    company_website                                       VARCHAR(255) NOT NULL,
    company_address                                       TEXT NOT NULL,
    tax_code_or_business_registration_number             VARCHAR(255) NOT NULL,
    company_phone                                         VARCHAR(20),
    company_logo_url                                      VARCHAR(1000),
    company_logo_public_id                                VARCHAR(500),
    company_documents_url                                 VARCHAR(500) NOT NULL,
    contact_person_phone                                  VARCHAR(20),
    contact_person_position                               VARCHAR(100) NOT NULL,
    company_size                                          VARCHAR(50) NOT NULL,
    industry                                              VARCHAR(200) NOT NULL,
    application_status                                    VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    application_date                                      TIMESTAMP NOT NULL DEFAULT NOW(),
    approval_date                                         TIMESTAMP,
    approved_by                                           BIGINT,
    rejection_reason                                      TEXT,
    created_at                                            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                                            TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Job postings
CREATE TABLE job_postings (
    id                    BIGSERIAL PRIMARY KEY,
    title                 VARCHAR(200) NOT NULL,
    description           TEXT NOT NULL,
    required_skills       TEXT NOT NULL,
    min_budget            DECIMAL(15,2) NOT NULL,
    max_budget            DECIMAL(15,2) NOT NULL,
    deadline              DATE NOT NULL,
    is_remote             BOOLEAN NOT NULL DEFAULT TRUE,
    location              VARCHAR(500),
    status                VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    applicant_count       INTEGER NOT NULL DEFAULT 0,
    experience_level      VARCHAR(50),
    job_type              VARCHAR(50),
    hiring_quantity       INTEGER,
    benefits              TEXT,
    gender_requirement   VARCHAR(20),
    is_negotiable         BOOLEAN DEFAULT FALSE,
    is_highlighted       BOOLEAN DEFAULT FALSE,
    paid_via_subscription BOOLEAN DEFAULT FALSE,
    posting_fee_charged   BOOLEAN DEFAULT FALSE,
    recruiter_id          BIGINT NOT NULL REFERENCES recruiter_profiles(user_id),
    created_at            TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP,
    closed_at             TIMESTAMP
);

CREATE INDEX idx_job_postings_recruiter_id ON job_postings(recruiter_id);
CREATE INDEX idx_job_postings_status ON job_postings(status);

-- Job applications
CREATE TABLE job_applications (
    id                  BIGSERIAL PRIMARY KEY,
    job_posting_id      BIGINT NOT NULL REFERENCES job_postings(id),
    user_id             BIGINT NOT NULL REFERENCES users(id),
    cover_letter        TEXT,
    applied_at          TIMESTAMP NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    acceptance_message  TEXT,
    rejection_reason    TEXT,
    reviewed_at         TIMESTAMP,
    processed_at        TIMESTAMP,
    interview_result    TEXT,
    offer_details       TEXT,
    offer_salary        BIGINT,
    offer_additional_requirements TEXT,
    candidate_offer_response TEXT,
    counter_salary_amount BIGINT,
    counter_additional_requirements TEXT,
    offer_round         INTEGER DEFAULT 0,
    CONSTRAINT uk_job_application_user_job UNIQUE (user_id, job_posting_id)
);

CREATE INDEX idx_job_applications_job_id ON job_applications(job_posting_id);
CREATE INDEX idx_job_applications_user_id ON job_applications(user_id);
CREATE INDEX idx_job_applications_status ON job_applications(status);

-- Job contracts
CREATE TABLE job_contracts (
    id                                     BIGSERIAL PRIMARY KEY,
    application_id                         BIGINT NOT NULL UNIQUE REFERENCES job_applications(id),
    status                                 VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    contract_type                          VARCHAR(20) NOT NULL,
    contract_number                        VARCHAR(50),
    job_title                              VARCHAR(300),
    working_location                       VARCHAR(500),
    candidate_position                      VARCHAR(200),
    job_description                         TEXT,
    probation_months                       INTEGER,
    probation_salary                       DECIMAL(15,2),
    probation_salary_text                  VARCHAR(500),
    probation_evaluation_criteria           TEXT,
    probation_objectives                   TEXT,
    salary                                 DECIMAL(15,2),
    salary_text                            VARCHAR(500),
    salary_payment_date                    INTEGER,
    payment_method                         VARCHAR(100),
    meal_allowance                         DECIMAL(15,2),
    transport_allowance                    DECIMAL(15,2),
    housing_allowance                      DECIMAL(15,2),
    other_allowances                       TEXT,
    bonus_policy                           TEXT,
    working_hours_per_day                  INTEGER,
    working_hours_per_week                 INTEGER,
    working_schedule                       VARCHAR(300),
    remote_work_policy                     TEXT,
    annual_leave_days                      INTEGER,
    leave_policy                           TEXT,
    insurance_policy                       TEXT,
    health_checkup_annual                  BOOLEAN,
    training_policy                        TEXT,
    other_benefits                         TEXT,
    legal_text                             TEXT,
    confidentiality_clause                 TEXT,
    ip_clause                              TEXT,
    non_compete_clause                     TEXT,
    non_compete_duration_months            INTEGER,
    termination_notice_days                INTEGER,
    termination_clause                     TEXT,
    employer_id                            BIGINT NOT NULL,
    employer_name                          VARCHAR(200),
    employer_company_name                  VARCHAR(300),
    employer_address                       VARCHAR(500),
    employer_tax_id                        VARCHAR(50),
    employer_email                         VARCHAR(200),
    candidate_id                           BIGINT NOT NULL,
    candidate_name                         VARCHAR(200),
    candidate_email                        VARCHAR(200),
    candidate_phone                        VARCHAR(30),
    candidate_address                      VARCHAR(500),
    candidate_date_of_birth                DATE,
    candidate_id_card_number               VARCHAR(50),
    candidate_id_card_place                VARCHAR(200),
    start_date                            DATE NOT NULL,
    end_date                              DATE,
    signed_pdf_url                        VARCHAR(500),
    signed_at                              TIMESTAMP,
    version                               BIGINT DEFAULT 0,
    created_at                            TIMESTAMP NOT NULL,
    updated_at                            TIMESTAMP
);

CREATE INDEX idx_job_contracts_employer_id ON job_contracts(employer_id);
CREATE INDEX idx_job_contracts_candidate_id ON job_contracts(candidate_id);
CREATE INDEX idx_job_contracts_status ON job_contracts(status);

-- Contract signatures
CREATE TABLE contract_signatures (
    id                  BIGSERIAL PRIMARY KEY,
    contract_id         BIGINT NOT NULL REFERENCES job_contracts(id) ON DELETE CASCADE,
    signed_by          BIGINT NOT NULL,
    signed_by_name     VARCHAR(200),
    signed_by_role     VARCHAR(20) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'NOT_SIGNED',
    signature_image_url VARCHAR(500),
    signed_at          TIMESTAMP,
    ip_address         VARCHAR(50),
    user_agent         VARCHAR(500)
);

CREATE INDEX idx_contract_signatures_contract_id ON contract_signatures(contract_id);
CREATE INDEX idx_contract_signatures_signed_by ON contract_signatures(signed_by);

-- Short term jobs
CREATE TABLE short_term_jobs (
    id                              BIGSERIAL PRIMARY KEY,
    title                           VARCHAR(200) NOT NULL,
    description                     TEXT NOT NULL,
    required_skills                 TEXT NOT NULL,
    budget                          DECIMAL(15,2) NOT NULL,
    is_negotiable                   BOOLEAN DEFAULT FALSE,
    payment_method                  VARCHAR(20),
    deadline                        TIMESTAMP NOT NULL,
    estimated_duration              VARCHAR(50),
    urgency                         VARCHAR(20),
    start_time                      TIMESTAMP,
    is_remote                       BOOLEAN NOT NULL DEFAULT TRUE,
    is_highlighted                  BOOLEAN DEFAULT FALSE,
    paid_via_subscription           BOOLEAN DEFAULT FALSE,
    location                        VARCHAR(500),
    status                          VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    applicant_count                INTEGER NOT NULL DEFAULT 0,
    selected_applicant_id           BIGINT,
    max_applicants                  INTEGER,
    min_rating                      DECIMAL(3,2),
    recruiter_id                    BIGINT NOT NULL REFERENCES recruiter_profiles(user_id),
    created_at                      TIMESTAMP NOT NULL,
    updated_at                      TIMESTAMP,
    published_at                    TIMESTAMP,
    completed_at                    TIMESTAMP,
    paid_at                         TIMESTAMP,
    cancellation_request_count     INTEGER NOT NULL DEFAULT 0,
    last_cancellation_request_at    TIMESTAMP,
    dispute_deadline_at             TIMESTAMP,
    is_banned                       BOOLEAN DEFAULT FALSE,
    ban_reason                      TEXT,
    banned_at                       TIMESTAMP,
    banned_by                       BIGINT
);

CREATE INDEX idx_short_term_jobs_recruiter_id ON short_term_jobs(recruiter_id);
CREATE INDEX idx_short_term_jobs_status ON short_term_jobs(status);
CREATE INDEX idx_short_term_jobs_deadline ON short_term_jobs(deadline);
CREATE INDEX idx_short_term_jobs_published_at ON short_term_jobs(published_at);
CREATE INDEX idx_short_term_jobs_is_banned ON short_term_jobs(is_banned);

-- Short term job applications
CREATE TABLE short_term_job_applications (
    id                               BIGSERIAL PRIMARY KEY,
    short_term_job_id                BIGINT NOT NULL REFERENCES short_term_jobs(id),
    user_id                          BIGINT NOT NULL REFERENCES users(id),
    cover_letter                     TEXT,
    proposed_price                   DECIMAL(15,2),
    proposed_duration                VARCHAR(100),
    portfolio                        TEXT,
    status                           VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    applied_at                       TIMESTAMP NOT NULL,
    accepted_at                      TIMESTAMP,
    started_at                       TIMESTAMP,
    submitted_at                     TIMESTAMP,
    completed_at                     TIMESTAMP,
    work_note                        TEXT,
    revision_count                   INTEGER DEFAULT 0,
    review_deadline_at               TIMESTAMP,
    response_deadline_at             TIMESTAMP,
    cancellation_requested_at        TIMESTAMP,
    cancellation_requested_by       BIGINT,
    dispute_eligibility_unlocked    BOOLEAN NOT NULL DEFAULT FALSE,
    last_activity_at                 TIMESTAMP,
    CONSTRAINT uk_short_term_app_user_job UNIQUE (user_id, short_term_job_id)
);

CREATE INDEX idx_short_term_job_applications_job_id ON short_term_job_applications(short_term_job_id);
CREATE INDEX idx_short_term_job_applications_user_id ON short_term_job_applications(user_id);
CREATE INDEX idx_short_term_job_applications_status ON short_term_job_applications(status);
CREATE INDEX idx_short_term_job_applications_applied_at ON short_term_job_applications(applied_at);

-- Short term job milestones
CREATE TABLE short_term_job_milestones (
    id               BIGSERIAL PRIMARY KEY,
    short_term_job_id BIGINT NOT NULL REFERENCES short_term_jobs(id) ON DELETE CASCADE,
    title            VARCHAR(200) NOT NULL,
    description      TEXT,
    amount           DECIMAL(15,2),
    deadline         TIMESTAMP NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    order_index      INTEGER NOT NULL,
    completed_at     TIMESTAMP,
    created_at       TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP
);

CREATE INDEX idx_short_term_job_milestones_job_id ON short_term_job_milestones(short_term_job_id);
CREATE INDEX idx_short_term_job_milestones_status ON short_term_job_milestones(status);

-- Job escrow
CREATE TABLE job_escrow (
    id                    BIGSERIAL PRIMARY KEY,
    job_id                BIGINT NOT NULL UNIQUE REFERENCES short_term_jobs(id),
    recruiter_id          BIGINT NOT NULL,
    worker_id            BIGINT,
    total_amount         DECIMAL(15,2) NOT NULL,
    platform_fee         DECIMAL(15,2) NOT NULL,
    fee_rate             DECIMAL(5,4) NOT NULL DEFAULT 0.1000,
    escrow_balance       DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    pending_payout_balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    status               VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    funded_at            TIMESTAMP,
    released_at          TIMESTAMP,
    refunded_at          TIMESTAMP,
    created_at           TIMESTAMP,
    updated_at           TIMESTAMP
);

CREATE INDEX idx_job_escrow_recruiter_id ON job_escrow(recruiter_id);
CREATE INDEX idx_job_escrow_worker_id ON job_escrow(worker_id);
CREATE INDEX idx_job_escrow_status ON job_escrow(status);

-- Escrow transactions
CREATE TABLE escrow_transactions (
    id               BIGSERIAL PRIMARY KEY,
    escrow_id        BIGINT NOT NULL REFERENCES job_escrow(id),
    transaction_type VARCHAR(30) NOT NULL,
    amount           DECIMAL(15,2) NOT NULL,
    fee_amount       DECIMAL(15,2) DEFAULT 0.00,
    net_amount       DECIMAL(15,2) NOT NULL,
    actor_id         BIGINT NOT NULL,
    actor_name       VARCHAR(200),
    reason           TEXT,
    metadata         TEXT,
    created_at       TIMESTAMP NOT NULL
);

CREATE INDEX idx_escrow_transactions_escrow_id ON escrow_transactions(escrow_id);
CREATE INDEX idx_escrow_transactions_actor_id ON escrow_transactions(actor_id);
CREATE INDEX idx_escrow_transactions_type ON escrow_transactions(transaction_type);

-- Job deliverables
CREATE TABLE job_deliverables (
    id             BIGSERIAL PRIMARY KEY,
    application_id BIGINT REFERENCES short_term_job_applications(id),
    milestone_id   BIGINT REFERENCES short_term_job_milestones(id),
    type          VARCHAR(20) NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    file_url      TEXT NOT NULL,
    file_size     BIGINT,
    mime_type     VARCHAR(100),
    description   TEXT,
    uploaded_by   BIGINT NOT NULL REFERENCES users(id),
    uploaded_at   TIMESTAMP NOT NULL
);

CREATE INDEX idx_job_deliverables_application_id ON job_deliverables(application_id);
CREATE INDEX idx_job_deliverables_milestone_id ON job_deliverables(milestone_id);
CREATE INDEX idx_job_deliverables_uploaded_by ON job_deliverables(uploaded_by);

-- Job disputes
CREATE TABLE job_disputes (
    id                            BIGSERIAL PRIMARY KEY,
    job_id                        BIGINT NOT NULL REFERENCES short_term_jobs(id),
    application_id                BIGINT REFERENCES short_term_job_applications(id),
    initiator_id                  BIGINT NOT NULL,
    respondent_id                  BIGINT NOT NULL,
    dispute_type                  VARCHAR(30) NOT NULL,
    reason                        TEXT NOT NULL,
    status                        VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    resolution                    VARCHAR(30),
    partial_refund_pct            DECIMAL(5,2),
    resolution_notes              TEXT,
    resolved_by                   BIGINT,
    resolved_at                   TIMESTAMP,
    created_at                    TIMESTAMP,
    admin_resolution_deadline_at   TIMESTAMP,
    escalation_level              INTEGER DEFAULT 0,
    priority                      VARCHAR(20) DEFAULT 'NORMAL',
    escalated_at                  TIMESTAMP
);

CREATE INDEX idx_job_disputes_job_id ON job_disputes(job_id);
CREATE INDEX idx_job_disputes_application_id ON job_disputes(application_id);
CREATE INDEX idx_job_disputes_initiator_id ON job_disputes(initiator_id);
CREATE INDEX idx_job_disputes_respondent_id ON job_disputes(respondent_id);
CREATE INDEX idx_job_disputes_status ON job_disputes(status);

-- Dispute evidence
CREATE TABLE dispute_evidence (
    id            BIGSERIAL PRIMARY KEY,
    dispute_id    BIGINT NOT NULL REFERENCES job_disputes(id) ON DELETE CASCADE,
    submitted_by  BIGINT NOT NULL,
    evidence_type VARCHAR(20) NOT NULL,
    content       TEXT,
    file_url      VARCHAR(500),
    file_name     VARCHAR(255),
    description   TEXT,
    is_official   BOOLEAN DEFAULT FALSE,
    created_at    TIMESTAMP NOT NULL
);

CREATE INDEX idx_dispute_evidence_dispute_id ON dispute_evidence(dispute_id);

-- Dispute responses
CREATE TABLE dispute_responses (
    id                 BIGSERIAL PRIMARY KEY,
    evidence_id        BIGINT NOT NULL REFERENCES dispute_evidence(id) ON DELETE CASCADE,
    responded_by       BIGINT NOT NULL,
    responded_by_name  VARCHAR(200),
    content            TEXT NOT NULL,
    is_admin_response  BOOLEAN DEFAULT FALSE,
    created_at         TIMESTAMP NOT NULL
);

CREATE INDEX idx_dispute_responses_evidence_id ON dispute_responses(evidence_id);

-- Job reviews
CREATE TABLE job_reviews (
    id                        BIGSERIAL PRIMARY KEY,
    application_id            BIGINT NOT NULL REFERENCES short_term_job_applications(id),
    reviewer_id               BIGINT NOT NULL REFERENCES users(id),
    reviewee_id               BIGINT NOT NULL REFERENCES users(id),
    review_type               VARCHAR(30) NOT NULL,
    rating                    INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment                   TEXT,
    strengths                 TEXT,
    improvements              TEXT,
    recommendations           TEXT,
    communication_rating      INTEGER CHECK (communication_rating BETWEEN 1 AND 5),
    quality_rating            INTEGER CHECK (quality_rating BETWEEN 1 AND 5),
    timeliness_rating         INTEGER CHECK (timeliness_rating BETWEEN 1 AND 5),
    professionalism_rating    INTEGER CHECK (professionalism_rating BETWEEN 1 AND 5),
    is_public                 BOOLEAN DEFAULT TRUE,
    created_at                TIMESTAMP NOT NULL,
    updated_at                TIMESTAMP,
    CONSTRAINT uk_job_review_app_reviewer UNIQUE (application_id, reviewer_id)
);

CREATE INDEX idx_job_reviews_application_id ON job_reviews(application_id);
CREATE INDEX idx_job_reviews_reviewer_id ON job_reviews(reviewer_id);
CREATE INDEX idx_job_reviews_reviewee_id ON job_reviews(reviewee_id);

-- Review windows
CREATE TABLE review_windows (
    id             BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL,
    job_id         BIGINT NOT NULL,
    started_at     TIMESTAMP NOT NULL,
    deadline       TIMESTAMP NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    auto_action_at TIMESTAMP,
    reminder_sent  BOOLEAN DEFAULT FALSE,
    approved_at    TIMESTAMP,
    auto_approved_at TIMESTAMP
);

CREATE INDEX idx_review_windows_application_id ON review_windows(application_id);
CREATE INDEX idx_review_windows_job_id ON review_windows(job_id);
CREATE INDEX idx_review_windows_status ON review_windows(status);
CREATE INDEX idx_review_windows_deadline ON review_windows(deadline);

-- Job boosts
CREATE TABLE job_boosts (
    id                 BIGSERIAL PRIMARY KEY,
    job_posting_id     BIGINT NOT NULL UNIQUE REFERENCES job_postings(id),
    recruiter_id       BIGINT NOT NULL,
    boost_status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    started_at         TIMESTAMP NOT NULL,
    expires_at         TIMESTAMP NOT NULL,
    scheduled_start_at TIMESTAMP,
    impressions       INTEGER NOT NULL DEFAULT 0,
    clicks             INTEGER NOT NULL DEFAULT 0,
    applications      INTEGER NOT NULL DEFAULT 0,
    created_at         TIMESTAMP NOT NULL,
    updated_at         TIMESTAMP NOT NULL,
    created_by         BIGINT NOT NULL
);

CREATE INDEX idx_job_boosts_recruiter_id ON job_boosts(recruiter_id);
CREATE INDEX idx_job_boosts_status ON job_boosts(boost_status);
CREATE INDEX idx_job_boosts_expires_at ON job_boosts(expires_at);

-- Revision notes
CREATE TABLE revision_notes (
    id             BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES short_term_job_applications(id) ON DELETE CASCADE,
    note           TEXT NOT NULL,
    specific_issues TEXT,
    requested_by   BIGINT NOT NULL REFERENCES users(id),
    requested_at   TIMESTAMP NOT NULL,
    resolved_at    TIMESTAMP
);

CREATE INDEX idx_revision_notes_application_id ON revision_notes(application_id);
CREATE INDEX idx_revision_notes_requested_by ON revision_notes(requested_by);

-- Job status audit logs
CREATE TABLE job_status_audit_logs (
    id               BIGSERIAL PRIMARY KEY,
    job_id           BIGINT,
    short_term_job_id BIGINT,
    application_id   BIGINT,
    previous_status  VARCHAR(50) NOT NULL,
    new_status       VARCHAR(50) NOT NULL,
    changed_by       BIGINT NOT NULL REFERENCES users(id),
    changed_by_role  VARCHAR(20) NOT NULL,
    reason           TEXT,
    metadata         TEXT,
    created_at       TIMESTAMP NOT NULL
);

CREATE INDEX idx_job_status_audit_logs_job_id ON job_status_audit_logs(job_id);
CREATE INDEX idx_job_status_audit_logs_short_term_job_id ON job_status_audit_logs(short_term_job_id);
CREATE INDEX idx_job_status_audit_logs_application_id ON job_status_audit_logs(application_id);
CREATE INDEX idx_job_status_audit_logs_changed_by ON job_status_audit_logs(changed_by);
CREATE INDEX idx_job_status_audit_logs_created_at ON job_status_audit_logs(created_at);

-- Recruiter shortlists
CREATE TABLE recruiter_shortlists (
    id              BIGSERIAL PRIMARY KEY,
    recruiter_id    BIGINT NOT NULL,
    candidate_id    BIGINT NOT NULL REFERENCES users(id),
    job_posting_id  BIGINT REFERENCES job_postings(id),
    notes           TEXT,
    shortlist_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL
);

CREATE INDEX idx_recruiter_shortlists_recruiter_id ON recruiter_shortlists(recruiter_id);
CREATE INDEX idx_recruiter_shortlists_candidate_id ON recruiter_shortlists(candidate_id);
CREATE INDEX idx_recruiter_shortlists_job_posting_id ON recruiter_shortlists(job_posting_id);
CREATE INDEX idx_recruiter_shortlists_status ON recruiter_shortlists(shortlist_status);

-- Recruitment sessions
CREATE TABLE recruitment_sessions (
    id                          BIGSERIAL PRIMARY KEY,
    recruiter_id                 BIGINT NOT NULL REFERENCES users(id),
    candidate_id                 BIGINT NOT NULL REFERENCES users(id),
    job_posting_id               BIGINT REFERENCES job_postings(id),
    short_term_job_id            BIGINT REFERENCES short_term_jobs(id),
    job_context_type             VARCHAR(30),
    job_context_id              BIGINT,
    status                       VARCHAR(20) NOT NULL DEFAULT 'CONTACTED',
    source_type                  VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    match_score                  INTEGER,
    skill_match_percent          INTEGER,
    candidate_title              VARCHAR(255),
    candidate_avatar            VARCHAR(500),
    recruiter_company           VARCHAR(255),
    job_title                   VARCHAR(255),
    last_message_at             TIMESTAMP,
    unread_count_recruiter      INTEGER DEFAULT 0,
    unread_count_candidate      INTEGER DEFAULT 0,
    is_archived_by_recruiter    BOOLEAN DEFAULT FALSE,
    is_archived_by_candidate   BOOLEAN DEFAULT FALSE,
    created_at                  TIMESTAMP NOT NULL,
    updated_at                  TIMESTAMP,
    CONSTRAINT uk_recruitment_session_recruiter_candidate_job
        UNIQUE (recruiter_id, candidate_id, job_posting_id)
);

CREATE INDEX idx_recruitment_sessions_recruiter_id ON recruitment_sessions(recruiter_id);
CREATE INDEX idx_recruitment_sessions_candidate_id ON recruitment_sessions(candidate_id);
CREATE INDEX idx_recruitment_sessions_status ON recruitment_sessions(status);
CREATE INDEX idx_recruitment_sessions_short_term_job_id ON recruitment_sessions(short_term_job_id);

-- Recruitment messages
CREATE TABLE recruitment_messages (
    id            BIGSERIAL PRIMARY KEY,
    session_id    BIGINT NOT NULL REFERENCES recruitment_sessions(id) ON DELETE CASCADE,
    sender_id     BIGINT NOT NULL REFERENCES users(id),
    sender_role   VARCHAR(20) NOT NULL,
    content       TEXT NOT NULL,
    message_type  VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    action_type   VARCHAR(100),
    action_data   TEXT,
    is_read       BOOLEAN NOT NULL DEFAULT FALSE,
    read_at       TIMESTAMP,
    created_at    TIMESTAMP NOT NULL
);

CREATE INDEX idx_recruitment_messages_session_id ON recruitment_messages(session_id);
CREATE INDEX idx_recruitment_messages_sender_id ON recruitment_messages(sender_id);
CREATE INDEX idx_recruitment_messages_created_at ON recruitment_messages(created_at);

-- Candidate search sessions
CREATE TABLE candidate_search_sessions (
    id            BIGSERIAL PRIMARY KEY,
    recruiter_id  BIGINT NOT NULL,
    search_query  TEXT,
    filters       JSONB,
    total_results INTEGER NOT NULL DEFAULT 0,
    page_size     INTEGER NOT NULL DEFAULT 20,
    searched_at   TIMESTAMP NOT NULL,
    ip_address    VARCHAR(45),
    user_agent    VARCHAR(500)
);

CREATE INDEX idx_candidate_search_sessions_recruiter_id ON candidate_search_sessions(recruiter_id);
CREATE INDEX idx_candidate_search_sessions_searched_at ON candidate_search_sessions(searched_at);

-- Candidate match scores
CREATE TABLE candidate_match_scores (
    id                     BIGSERIAL PRIMARY KEY,
    job_posting_id         BIGINT NOT NULL REFERENCES job_postings(id),
    candidate_id           BIGINT NOT NULL REFERENCES users(id),
    skill_match_score      DECIMAL(5,4) DEFAULT 0.0000,
    experience_match_score  DECIMAL(5,4) DEFAULT 0.0000,
    budget_match_score     DECIMAL(5,4) DEFAULT 0.0000,
    premium_bonus_score    DECIMAL(5,4) DEFAULT 0.0000,
    total_score            DECIMAL(5,4) DEFAULT 0.0000,
    ai_fit_summary         TEXT,
    ai_skill_signals       JSONB,
    ai_reasoning           TEXT,
    calculated_at          TIMESTAMP NOT NULL,
    is_recalculated        BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_candidate_match_score_job_candidate UNIQUE (job_posting_id, candidate_id)
);

CREATE INDEX idx_candidate_match_scores_job_posting_id ON candidate_match_scores(job_posting_id);
CREATE INDEX idx_candidate_match_scores_candidate_id ON candidate_match_scores(candidate_id);

-- Interview schedules
CREATE TABLE interview_schedules (
    id               BIGSERIAL PRIMARY KEY,
    application_id   BIGINT NOT NULL UNIQUE REFERENCES job_applications(id),
    scheduled_at     TIMESTAMP NOT NULL,
    duration_minutes INTEGER DEFAULT 60,
    meeting_type     VARCHAR(20) NOT NULL,
    meeting_link     VARCHAR(500),
    skillverse_room_id VARCHAR(100),
    location        VARCHAR(500),
    interviewer_name VARCHAR(200),
    interview_notes TEXT,
    response_deadline_at TIMESTAMP,
    responded_at     TIMESTAMP,
    cancelled_by     VARCHAR(20),
    cancel_reason    TEXT,
    completed_at     TIMESTAMP,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP
);

CREATE INDEX idx_interview_schedules_application_id ON interview_schedules(application_id);
CREATE INDEX idx_interview_schedules_status ON interview_schedules(status);
CREATE INDEX idx_interview_schedules_scheduled_at ON interview_schedules(scheduled_at);

-- Trust scores
CREATE TABLE trust_scores (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    total_score         DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    trust_tier          VARCHAR(20),
    completion_rate     DECIMAL(5,4) DEFAULT 0.0000,
    avg_rating          DECIMAL(3,2) DEFAULT 0.00,
    dispute_rate        DECIMAL(5,4) DEFAULT 0.0000,
    total_jobs          INTEGER NOT NULL DEFAULT 0,
    completed_jobs      INTEGER NOT NULL DEFAULT 0,
    disputed_jobs       INTEGER NOT NULL DEFAULT 0,
    total_reviews       INTEGER NOT NULL DEFAULT 0,
    account_age_days    INTEGER DEFAULT 0,
    response_time_hours DECIMAL(8,2) DEFAULT 0.00,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL
);

CREATE INDEX idx_trust_scores_user_id ON trust_scores(user_id);
CREATE INDEX idx_trust_scores_tier ON trust_scores(trust_tier);

-- ============================================================
-- SECTION 5: MENTOR SERVICE & MENTOR BOOKING SERVICE
-- ============================================================

-- Mentor profiles
CREATE TABLE mentor_profiles (
    user_id               BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    full_name             VARCHAR(255) NOT NULL,
    email                 VARCHAR(255) NOT NULL,
    linkedin_profile      VARCHAR(500),
    main_expertise_areas  TEXT NOT NULL,
    years_of_experience   INTEGER NOT NULL,
    personal_profile      TEXT NOT NULL,
    cv_portfolio_url      VARCHAR(500),
    certificates_url      VARCHAR(500),
    avatar_url            VARCHAR(500),
    signature_url         VARCHAR(500),
    github_profile        VARCHAR(500),
    website_url           VARCHAR(500),
    skills                TEXT,
    achievements          TEXT,
    skill_points          INTEGER NOT NULL DEFAULT 0,
    current_level         INTEGER NOT NULL DEFAULT 0,
    badges                TEXT,
    application_status    VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    pre_chat_enabled      BOOLEAN DEFAULT TRUE,
    rating_average        DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    rating_count          INTEGER NOT NULL DEFAULT 0,
    application_date      TIMESTAMP NOT NULL DEFAULT NOW(),
    approval_date         TIMESTAMP,
    approved_by          BIGINT,
    rejection_reason      VARCHAR(500),
    expertise_areas       TEXT,
    certifications        TEXT,
    hourly_rate           DOUBLE PRECISION,
    availability          TEXT,
    bio                   TEXT,
    languages_spoken      VARCHAR(255),
    linkedin_url          VARCHAR(500),
    portfolio_url         VARCHAR(500),
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_mentor_profiles_application_status ON mentor_profiles(application_status);

-- Favorite mentors
CREATE TABLE favorite_mentors (
    id         BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES users(id),
    mentor_id  BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_favorite_mentors UNIQUE (student_id, mentor_id)
);

CREATE INDEX idx_favorite_mentors_student_id ON favorite_mentors(student_id);
CREATE INDEX idx_favorite_mentors_mentor_id ON favorite_mentors(mentor_id);

-- Mentor availability
CREATE TABLE mentor_availability (
    id                 BIGSERIAL PRIMARY KEY,
    mentor_id          BIGINT NOT NULL,
    start_time         TIMESTAMP NOT NULL,
    end_time           TIMESTAMP NOT NULL,
    is_recurring       BOOLEAN DEFAULT FALSE,
    recurrence_type    VARCHAR(20),
    recurrence_end_date TIMESTAMP
);

CREATE INDEX idx_mentor_availability_mentor_id ON mentor_availability(mentor_id);
CREATE INDEX idx_mentor_availability_start_time ON mentor_availability(start_time);

-- Mentor bookings
CREATE TABLE mentor_bookings (
    id                   BIGSERIAL PRIMARY KEY,
    mentor_id            BIGINT NOT NULL REFERENCES users(id),
    learner_id           BIGINT NOT NULL REFERENCES users(id),
    start_time           TIMESTAMP NOT NULL,
    end_time             TIMESTAMP NOT NULL,
    duration_minutes     INTEGER NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    price_vnd            DECIMAL(12,2) NOT NULL,
    meeting_link         VARCHAR(255),
    payment_reference    VARCHAR(50),
    confirmed_by_learner BOOLEAN DEFAULT FALSE,
    mentor_completed_at  TIMESTAMP,
    learner_confirmed_at TIMESTAMP,
    learner_completed_at TIMESTAMP,
    completion_deadline  TIMESTAMP,
    created_at           TIMESTAMP NOT NULL,
    updated_at           TIMESTAMP NOT NULL
);

CREATE INDEX idx_mentor_bookings_mentor_status_time ON mentor_bookings(mentor_id, status, start_time);
CREATE INDEX idx_mentor_bookings_learner_status_time ON mentor_bookings(learner_id, status, start_time);
CREATE INDEX idx_mentor_bookings_status ON mentor_bookings(status);

-- Booking reviews
CREATE TABLE booking_reviews (
    id           BIGSERIAL PRIMARY KEY,
    booking_id   BIGINT NOT NULL UNIQUE REFERENCES mentor_bookings(id),
    student_id   BIGINT NOT NULL REFERENCES users(id),
    mentor_id    BIGINT NOT NULL REFERENCES users(id),
    rating       INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment      TEXT,
    reply        TEXT,
    is_anonymous BOOLEAN DEFAULT FALSE,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_booking_reviews_booking_id ON booking_reviews(booking_id);
CREATE INDEX idx_booking_reviews_mentor_id ON booking_reviews(mentor_id);

-- Booking disputes
CREATE TABLE booking_disputes (
    id                    BIGSERIAL PRIMARY KEY,
    booking_id            BIGINT NOT NULL UNIQUE REFERENCES mentor_bookings(id),
    initiator_id          BIGINT NOT NULL,
    respondent_id         BIGINT NOT NULL,
    reason                TEXT NOT NULL,
    status                VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    resolution            VARCHAR(30),
    resolution_notes      TEXT,
    refund_amount         DECIMAL(12,2),
    released_amount       DECIMAL(12,2),
    mentor_payout_amount  DECIMAL(12,2),
    admin_commission_amount DECIMAL(12,2),
    resolved_by           BIGINT,
    resolved_at           TIMESTAMP,
    created_at            TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_booking_disputes_booking_id ON booking_disputes(booking_id);
CREATE INDEX idx_booking_disputes_initiator_id ON booking_disputes(initiator_id);
CREATE INDEX idx_booking_disputes_respondent_id ON booking_disputes(respondent_id);
CREATE INDEX idx_booking_disputes_status ON booking_disputes(status);

-- Booking dispute evidence
CREATE TABLE booking_dispute_evidence (
    id            BIGSERIAL PRIMARY KEY,
    dispute_id    BIGINT NOT NULL REFERENCES booking_disputes(id),
    submitted_by  BIGINT NOT NULL,
    evidence_type VARCHAR(20) NOT NULL,
    content       TEXT,
    file_url      VARCHAR(500),
    file_name     VARCHAR(255),
    description   TEXT,
    is_official   BOOLEAN DEFAULT FALSE,
    review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    review_notes  TEXT,
    reviewed_by   BIGINT,
    reviewed_at  TIMESTAMP,
    created_at   TIMESTAMP NOT NULL
);

CREATE INDEX idx_booking_dispute_evidence_dispute_id ON booking_dispute_evidence(dispute_id);

-- Booking dispute responses
CREATE TABLE booking_dispute_responses (
    id               BIGSERIAL PRIMARY KEY,
    evidence_id      BIGINT NOT NULL REFERENCES booking_dispute_evidence(id),
    responded_by     BIGINT NOT NULL,
    responded_by_name VARCHAR(200),
    content          TEXT NOT NULL,
    is_admin_response BOOLEAN DEFAULT FALSE,
    created_at       TIMESTAMP NOT NULL
);

CREATE INDEX idx_booking_dispute_responses_evidence_id ON booking_dispute_responses(evidence_id);

-- ============================================================
-- SECTION 6: PREMIUM SERVICE
-- ============================================================

-- Premium plans
CREATE TABLE premium_plans (
    id                            BIGSERIAL PRIMARY KEY,
    name                          VARCHAR(100) NOT NULL UNIQUE,
    display_name                  VARCHAR(150) NOT NULL,
    description                   TEXT,
    duration_months               INTEGER NOT NULL,
    price                         DECIMAL(12,2) NOT NULL,
    currency                      VARCHAR(10) NOT NULL DEFAULT 'VND',
    plan_type                     VARCHAR(20) NOT NULL,
    target_role                   VARCHAR(20) NOT NULL DEFAULT 'LEARNER',
    student_discount_percent      DECIMAL(5,2) DEFAULT 0.00,
    discount_percent              DECIMAL(5,2),
    features                      TEXT,
    is_active                     BOOLEAN NOT NULL DEFAULT TRUE,
    max_subscribers               INTEGER,
    created_at                    TIMESTAMP NOT NULL,
    updated_at                    TIMESTAMP NOT NULL
);

CREATE INDEX idx_premium_plans_name ON premium_plans(name);
CREATE INDEX idx_premium_plans_is_active ON premium_plans(is_active);
CREATE INDEX idx_premium_plans_price ON premium_plans(price);

-- User subscriptions
CREATE TABLE user_subscriptions (
    id                           BIGSERIAL PRIMARY KEY,
    user_id                      BIGINT NOT NULL REFERENCES users(id),
    plan_id                      BIGINT NOT NULL REFERENCES premium_plans(id),
    start_date                   TIMESTAMP NOT NULL,
    end_date                     TIMESTAMP NOT NULL,
    is_active                    BOOLEAN NOT NULL DEFAULT TRUE,
    payment_txn_id              BIGINT,
    status                       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_student_subscription      BOOLEAN NOT NULL DEFAULT FALSE,
    is_discounted_pricing       BOOLEAN,
    auto_renew                   BOOLEAN NOT NULL DEFAULT FALSE,
    renewal_price_snapshot       DECIMAL(12,2),
    renewal_price_locked_at      TIMESTAMP,
    current_cycle_paid_amount_snapshot DECIMAL(12,2),
    cancellation_reason          VARCHAR(500),
    cancelled_at                 TIMESTAMP,
    created_at                   TIMESTAMP NOT NULL,
    updated_at                   TIMESTAMP NOT NULL
);

CREATE INDEX idx_user_subscriptions_user_active ON user_subscriptions(user_id, is_active);
CREATE INDEX idx_user_subscriptions_plan_id ON user_subscriptions(plan_id);
CREATE INDEX idx_user_subscriptions_dates ON user_subscriptions(start_date, end_date);

-- Plan feature limits
CREATE TABLE plan_feature_limits (
    id               BIGSERIAL PRIMARY KEY,
    plan_id          BIGINT NOT NULL REFERENCES premium_plans(id),
    feature_type     VARCHAR(50) NOT NULL,
    limit_value      INTEGER,
    reset_period     VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    is_unlimited     BOOLEAN NOT NULL DEFAULT FALSE,
    bonus_multiplier DECIMAL(5,2) DEFAULT 1.00,
    description      TEXT,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP NOT NULL,
    CONSTRAINT uk_plan_feature UNIQUE (plan_id, feature_type)
);

CREATE INDEX idx_plan_feature_limits_plan_id ON plan_feature_limits(plan_id);
CREATE INDEX idx_plan_feature_limits_feature_type ON plan_feature_limits(feature_type);

-- Subscription cancellations
CREATE TABLE subscription_cancellations (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    subscription_id     BIGINT,
    cancellation_month  VARCHAR(7) NOT NULL,
    refund_percentage   INTEGER NOT NULL,
    refund_amount       DECIMAL(12,2),
    days_since_purchase BIGINT,
    reason              VARCHAR(500),
    cancellation_type   VARCHAR(30) NOT NULL,
    created_at          TIMESTAMP NOT NULL,
    FOREIGN KEY (subscription_id) REFERENCES user_subscriptions(id) ON DELETE SET NULL
);

CREATE INDEX idx_subscription_cancellations_user_month ON subscription_cancellations(user_id, cancellation_month);
CREATE INDEX idx_subscription_cancellations_month ON subscription_cancellations(cancellation_month);

-- User usage tracking
CREATE TABLE user_usage_tracking (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT NOT NULL REFERENCES users(id),
    feature_type         VARCHAR(50) NOT NULL,
    usage_count          INTEGER NOT NULL DEFAULT 0,
    last_reset_at        TIMESTAMP NOT NULL,
    current_period_start TIMESTAMP NOT NULL,
    current_period_end   TIMESTAMP NOT NULL,
    created_at           TIMESTAMP NOT NULL,
    updated_at           TIMESTAMP NOT NULL,
    CONSTRAINT uk_user_feature UNIQUE (user_id, feature_type)
);

CREATE INDEX idx_user_usage_tracking_user_id ON user_usage_tracking(user_id);
CREATE INDEX idx_user_usage_tracking_feature_type ON user_usage_tracking(feature_type);
CREATE INDEX idx_user_usage_tracking_period ON user_usage_tracking(current_period_start, current_period_end);

-- ============================================================
-- SECTION 7: WALLET SERVICE
-- ============================================================

-- Wallets
CREATE TABLE wallets (
    wallet_id                 BIGSERIAL PRIMARY KEY,
    user_id                   BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    cash_balance              DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    coin_balance              BIGINT NOT NULL DEFAULT 0,
    total_deposited           DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    total_withdrawn           DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    total_coins_earned        BIGINT NOT NULL DEFAULT 0,
    total_coins_spent         BIGINT NOT NULL DEFAULT 0,
    status                    VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    frozen_cash_balance       DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    escrow_balance            DECIMAL(15,2) DEFAULT 0.00,
    pending_payout_balance    DECIMAL(15,2) DEFAULT 0.00,
    bank_name                 VARCHAR(50),
    bank_account_number       VARCHAR(50),
    bank_account_name         VARCHAR(100),
    transaction_pin           VARCHAR(255),
    require_2fa               BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                TIMESTAMP NOT NULL,
    updated_at                TIMESTAMP NOT NULL,
    last_transaction_at       TIMESTAMP
);

CREATE INDEX idx_wallets_user_id ON wallets(user_id);
CREATE INDEX idx_wallets_status ON wallets(status);

-- Wallet transactions
CREATE TABLE wallet_transactions (
    transaction_id       BIGSERIAL PRIMARY KEY,
    wallet_id            BIGINT NOT NULL REFERENCES wallets(wallet_id),
    transaction_type     VARCHAR(30) NOT NULL,
    currency_type        VARCHAR(10) NOT NULL,
    cash_amount          DECIMAL(15,2),
    coin_amount          BIGINT,
    cash_balance_after   DECIMAL(15,2),
    coin_balance_after   BIGINT,
    description          VARCHAR(500) NOT NULL,
    notes                VARCHAR(1000),
    reference_type       VARCHAR(50),
    reference_id         VARCHAR(100),
    metadata             TEXT,
    status               VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    fee                  DECIMAL(15,2) DEFAULT 0.00,
    ip_address           VARCHAR(50),
    user_agent           VARCHAR(255),
    created_at           TIMESTAMP NOT NULL,
    processed_at         TIMESTAMP
);

CREATE INDEX idx_wallet_transactions_wallet_id ON wallet_transactions(wallet_id);
CREATE INDEX idx_wallet_transactions_type ON wallet_transactions(transaction_type);
CREATE INDEX idx_wallet_transactions_created_at ON wallet_transactions(created_at);
CREATE INDEX idx_wallet_transactions_reference ON wallet_transactions(reference_type, reference_id);

-- Withdrawal requests
CREATE TABLE withdrawal_requests (
    request_id             BIGSERIAL PRIMARY KEY,
    request_code           VARCHAR(50) NOT NULL UNIQUE,
    user_id                BIGINT NOT NULL REFERENCES users(id),
    wallet_id              BIGINT NOT NULL REFERENCES wallets(wallet_id),
    amount                 DECIMAL(15,2) NOT NULL,
    fee                    DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    net_amount             DECIMAL(15,2) NOT NULL,
    status                 VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    bank_name              VARCHAR(50) NOT NULL,
    bank_account_number    VARCHAR(50) NOT NULL,
    bank_account_name      VARCHAR(100) NOT NULL,
    bank_branch            VARCHAR(100),
    reason                 VARCHAR(500),
    user_notes             VARCHAR(1000),
    pin_verified           BOOLEAN NOT NULL DEFAULT FALSE,
    two_fa_verified        BOOLEAN NOT NULL DEFAULT FALSE,
    approved_by            BIGINT,
    approved_at            TIMESTAMP,
    admin_notes            VARCHAR(1000),
    rejection_reason       VARCHAR(1000),
    bank_transaction_id    VARCHAR(100),
    completed_at           TIMESTAMP,
    wallet_transaction_id  BIGINT,
    priority               INTEGER NOT NULL DEFAULT 3,
    ip_address             VARCHAR(50),
    user_agent             VARCHAR(255),
    retry_count            INTEGER NOT NULL DEFAULT 0,
    last_retry_at          TIMESTAMP,
    error_message          TEXT,
    created_at             TIMESTAMP NOT NULL,
    updated_at             TIMESTAMP NOT NULL,
    expires_at             TIMESTAMP
);

CREATE INDEX idx_withdrawal_requests_user_id ON withdrawal_requests(user_id);
CREATE INDEX idx_withdrawal_requests_status ON withdrawal_requests(status);
CREATE INDEX idx_withdrawal_requests_created_at ON withdrawal_requests(created_at);

-- ============================================================
-- SECTION 8: PAYMENT SERVICE
-- ============================================================

-- Payment transactions
CREATE TABLE payment_transactions (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT NOT NULL REFERENCES users(id),
    amount             DECIMAL(12,2) NOT NULL,
    currency           VARCHAR(10) NOT NULL DEFAULT 'VND',
    type               VARCHAR(20) NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payment_method     VARCHAR(20) NOT NULL,
    reference_id       VARCHAR(100),
    internal_reference VARCHAR(50) UNIQUE,
    description        VARCHAR(255),
    metadata           TEXT,
    failure_reason     VARCHAR(500),
    created_at         TIMESTAMP NOT NULL,
    updated_at         TIMESTAMP NOT NULL
);

CREATE INDEX idx_payment_transactions_user_status ON payment_transactions(user_id, status);
CREATE INDEX idx_payment_transactions_method_status ON payment_transactions(payment_method, status);
CREATE INDEX idx_payment_transactions_reference_id ON payment_transactions(reference_id);
CREATE INDEX idx_payment_transactions_created_at ON payment_transactions(created_at);

-- ============================================================
-- SECTION 9: PORTFOLIO SERVICE
-- ============================================================

-- Portfolio extended profiles
CREATE TABLE portfolio_extended_profiles (
    user_id                   BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    full_name                 VARCHAR(200),
    bio                       TEXT,
    phone                     VARCHAR(50),
    address                   VARCHAR(300),
    region                    VARCHAR(100),
    company_id                BIGINT,
    social_links              TEXT,
    professional_title        VARCHAR(200),
    career_goals             TEXT,
    years_of_experience       INTEGER,
    work_experiences         TEXT,
    education_history        TEXT,
    avatar_url               VARCHAR(500),
    avatar_public_id          VARCHAR(500),
    video_intro_url          VARCHAR(500),
    video_intro_public_id    VARCHAR(500),
    cover_image_url          VARCHAR(500),
    cover_image_public_id    VARCHAR(500),
    linkedin_url             VARCHAR(500),
    github_url               VARCHAR(500),
    portfolio_website_url    VARCHAR(500),
    behance_url              VARCHAR(500),
    dribbble_url             VARCHAR(500),
    tagline                  VARCHAR(300),
    location                  VARCHAR(100),
    availability_status      VARCHAR(50),
    hourly_rate              DOUBLE PRECISION,
    preferred_currency       VARCHAR(10),
    top_skills               TEXT,
    languages_spoken          TEXT,
    is_public                BOOLEAN DEFAULT TRUE,
    show_contact_info        BOOLEAN DEFAULT FALSE,
    allow_job_offers         BOOLEAN DEFAULT TRUE,
    theme_preference         VARCHAR(50),
    portfolio_views          BIGINT DEFAULT 0,
    total_projects           INTEGER DEFAULT 0,
    total_certificates       INTEGER DEFAULT 0,
    custom_url_slug          VARCHAR(255) UNIQUE,
    meta_description         VARCHAR(500),
    keywords                 TEXT,
    created_at                TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Portfolio projects
CREATE TABLE portfolio_projects (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    client_name     VARCHAR(255),
    project_type    VARCHAR(30) NOT NULL,
    duration        VARCHAR(100),
    completion_date DATE,
    project_url     VARCHAR(1000),
    github_url      VARCHAR(1000),
    thumbnail_url   VARCHAR(1000),
    thumbnail_public_id VARCHAR(500),
    rating          INTEGER CHECK (rating BETWEEN 1 AND 5),
    client_feedback TEXT,
    is_featured     BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_portfolio_projects_user_id ON portfolio_projects(user_id);

-- Project tools (element collection)
CREATE TABLE project_tools (
    project_id BIGINT NOT NULL REFERENCES portfolio_projects(id) ON DELETE CASCADE,
    tool       VARCHAR(255),
    PRIMARY KEY (project_id, tool)
);

-- Project outcomes (element collection)
CREATE TABLE project_outcomes (
    project_id BIGINT NOT NULL REFERENCES portfolio_projects(id) ON DELETE CASCADE,
    outcome    TEXT,
    PRIMARY KEY (project_id, outcome)
);

-- Project attachments (embeddable element collection)
CREATE TABLE project_attachments (
    project_id     BIGINT NOT NULL REFERENCES portfolio_projects(id) ON DELETE CASCADE,
    file_name      VARCHAR(500),
    file_url       VARCHAR(1000),
    file_public_id VARCHAR(500),
    file_type      VARCHAR(50),
    PRIMARY KEY (project_id, file_name)
);

-- External certificates
CREATE TABLE external_certificates (
    id                       BIGSERIAL PRIMARY KEY,
    user_id                  BIGINT NOT NULL REFERENCES users(id),
    title                    VARCHAR(500) NOT NULL,
    issuing_organization     VARCHAR(255) NOT NULL,
    issue_date               DATE,
    expiry_date              DATE,
    credential_id            VARCHAR(255),
    credential_url          VARCHAR(1000),
    description             TEXT,
    certificate_image_url   VARCHAR(1000),
    certificate_image_public_id VARCHAR(500),
    category                 VARCHAR(50),
    is_verified              BOOLEAN DEFAULT FALSE,
    created_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_external_certificates_user_id ON external_certificates(user_id);

-- Certificate skills (element collection)
CREATE TABLE certificate_skills (
    certificate_id BIGINT NOT NULL REFERENCES external_certificates(id) ON DELETE CASCADE,
    skill          VARCHAR(255),
    PRIMARY KEY (certificate_id, skill)
);

-- Generated CVs
CREATE TABLE generated_cvs (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    cv_content      TEXT NOT NULL,
    cv_json         TEXT,
    template_name   VARCHAR(100),
    is_active       BOOLEAN DEFAULT TRUE,
    version         INTEGER NOT NULL DEFAULT 1,
    generated_by_ai BOOLEAN DEFAULT FALSE,
    ai_prompt       TEXT,
    pdf_url         VARCHAR(1000),
    pdf_public_id   VARCHAR(500),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_generated_cvs_user_id ON generated_cvs(user_id);

-- Mentor reviews
CREATE TABLE mentor_reviews (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT NOT NULL REFERENCES users(id),
    mentor_id      BIGINT NOT NULL REFERENCES users(id),
    feedback       TEXT NOT NULL,
    skill_endorsed VARCHAR(255),
    rating         INTEGER CHECK (rating BETWEEN 1 AND 5),
    is_verified    BOOLEAN DEFAULT FALSE,
    is_public      BOOLEAN DEFAULT TRUE,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_mentor_reviews_user_id ON mentor_reviews(user_id);
CREATE INDEX idx_mentor_reviews_mentor_id ON mentor_reviews(mentor_id);

-- Mentor batch verification requests
CREATE TABLE mentor_batch_verification_requests (
    id                  BIGSERIAL PRIMARY KEY,
    mentor_id           BIGINT NOT NULL REFERENCES users(id),
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CONSTRAINT chk_mbvr_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'PARTIAL_APPROVED', 'COMPLETED', 'REVOKED')),
    github_url          VARCHAR(500),
    portfolio_url       VARCHAR(500),
    additional_notes    TEXT,
    general_review_note TEXT,
    submitted_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    reviewed_by         BIGINT REFERENCES users(id),
    reviewed_at         TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_mbvr_mentor_status ON mentor_batch_verification_requests(mentor_id, status);
CREATE INDEX idx_mbvr_status_submitted ON mentor_batch_verification_requests(status, submitted_at);

-- Mentor skill verification requests
CREATE TABLE mentor_skill_verification_requests (
    id               BIGSERIAL PRIMARY KEY,
    mentor_id        BIGINT NOT NULL REFERENCES users(id),
    batch_request_id BIGINT REFERENCES mentor_batch_verification_requests(id) ON DELETE CASCADE,
    skill_name       VARCHAR(100) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CONSTRAINT chk_msvr_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'PARTIAL_APPROVED', 'COMPLETED', 'REVOKED')),
    github_url       VARCHAR(500),
    portfolio_url    VARCHAR(500),
    additional_notes TEXT,
    review_note      TEXT,
    reviewed_by      BIGINT REFERENCES users(id),
    requested_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    reviewed_at      TIMESTAMP,
    updated_at       TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_msvr_mentor_status ON mentor_skill_verification_requests(mentor_id, status);
CREATE INDEX idx_msvr_status_requested ON mentor_skill_verification_requests(status, requested_at);
CREATE INDEX idx_msvr_batch ON mentor_skill_verification_requests(batch_request_id);

-- Mentor verification evidences
CREATE TABLE mentor_verification_evidences (
    id                      BIGSERIAL PRIMARY KEY,
    verification_request_id BIGINT REFERENCES mentor_skill_verification_requests(id) ON DELETE CASCADE,
    batch_request_id        BIGINT REFERENCES mentor_batch_verification_requests(id) ON DELETE CASCADE,
    evidence_type           VARCHAR(30) NOT NULL,
    evidence_url            VARCHAR(1000),
    description             TEXT,
    certificate_id          BIGINT REFERENCES external_certificates(id),
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_mve_type CHECK (evidence_type IN ('CERTIFICATE', 'GITHUB', 'PORTFOLIO_LINK', 'WORK_EXPERIENCE', 'CV')),
    CONSTRAINT chk_mve_owner_request CHECK (verification_request_id IS NOT NULL OR batch_request_id IS NOT NULL)
);

CREATE INDEX idx_mve_request ON mentor_verification_evidences(verification_request_id);
CREATE INDEX idx_mve_batch ON mentor_verification_evidences(batch_request_id);

-- Student skill verification requests
CREATE TABLE student_skill_verification_requests (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES users(id),
    skill_name       VARCHAR(100) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    github_url       VARCHAR(500),
    portfolio_url    VARCHAR(500),
    additional_notes TEXT,
    review_note      TEXT,
    reviewed_by      BIGINT REFERENCES users(id),
    requested_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    reviewed_at      TIMESTAMP,
    updated_at       TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_ssvr_user_status ON student_skill_verification_requests(user_id, status);
CREATE INDEX idx_ssvr_status_requested ON student_skill_verification_requests(status, requested_at);

-- Student verification evidences
CREATE TABLE student_verification_evidences (
    id                      BIGSERIAL PRIMARY KEY,
    verification_request_id BIGINT NOT NULL REFERENCES student_skill_verification_requests(id) ON DELETE CASCADE,
    evidence_type           VARCHAR(30) NOT NULL,
    evidence_url            VARCHAR(1000),
    description             TEXT,
    certificate_id          BIGINT REFERENCES external_certificates(id),
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_sve_type CHECK (evidence_type IN ('CERTIFICATE', 'GITHUB', 'PORTFOLIO_LINK', 'WORK_EXPERIENCE', 'CV'))
);

CREATE INDEX idx_sve_request ON student_verification_evidences(verification_request_id);

-- ============================================================
-- SECTION 10: AI SERVICE
-- ============================================================

-- Chat sessions
CREATE TABLE chat_sessions (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    chat_mode             VARCHAR(40) NOT NULL DEFAULT 'GENERAL_CAREER_ADVISOR',
    custom_title          VARCHAR(100),
    domain                VARCHAR(255),
    industry              VARCHAR(255),
    job_role              VARCHAR(255),
    expert_prompt_config_id BIGINT,
    taxonomy_entry_id     BIGINT,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP,
    last_message_at       TIMESTAMP NOT NULL
);

CREATE INDEX idx_chat_sessions_user_id ON chat_sessions(user_id);
CREATE INDEX idx_chat_sessions_user_last_message ON chat_sessions(user_id, last_message_at);
CREATE INDEX idx_chat_sessions_mode ON chat_sessions(chat_mode);
CREATE INDEX idx_chat_sessions_expert_prompt_config_id ON chat_sessions(expert_prompt_config_id);
CREATE INDEX idx_chat_sessions_taxonomy_entry_id ON chat_sessions(taxonomy_entry_id);

-- Chat messages
CREATE TABLE chat_messages (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id   BIGINT NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    user_message VARCHAR(2000) NOT NULL,
    ai_response  TEXT NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_chat_messages_user_session ON chat_messages(user_id, session_id);
CREATE INDEX idx_chat_messages_created_at ON chat_messages(created_at);

-- Roadmap sessions
CREATE TABLE roadmap_sessions (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title                   VARCHAR(255) NOT NULL,
    schema_version          INTEGER NOT NULL DEFAULT 2,
    original_goal           TEXT,
    validated_goal          TEXT,
    duration                VARCHAR(50),
    experience_level        VARCHAR(100),
    learning_style          VARCHAR(150),
    roadmap_type            VARCHAR(20),
    roadmap_mode            VARCHAR(20),
    target                  TEXT,
    final_objective         TEXT,
    total_nodes             INTEGER,
    total_estimated_hours   DOUBLE PRECISION,
    difficulty_level        VARCHAR(20),
    is_premium_generated    BOOLEAN NOT NULL DEFAULT FALSE,
    status                  VARCHAR(20),
    roadmap_json            JSONB NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_roadmap_sessions_user_id ON roadmap_sessions(user_id);
CREATE INDEX idx_roadmap_sessions_created_at ON roadmap_sessions(created_at);

-- User roadmap progress
CREATE TABLE user_roadmap_progress (
    id                 BIGSERIAL PRIMARY KEY,
    roadmap_session_id BIGINT NOT NULL REFERENCES roadmap_sessions(id) ON DELETE CASCADE,
    quest_id           VARCHAR(255) NOT NULL,
    status             VARCHAR(50) NOT NULL DEFAULT 'NOT_STARTED',
    progress           INTEGER NOT NULL DEFAULT 0,
    completed_at       TIMESTAMP WITH TIME ZONE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_roadmap_quest UNIQUE (roadmap_session_id, quest_id)
);

CREATE INDEX idx_user_roadmap_progress_session_id ON user_roadmap_progress(roadmap_session_id);
CREATE INDEX idx_user_roadmap_progress_quest_id ON user_roadmap_progress(quest_id);

-- Expert prompt configs
CREATE TABLE expert_prompt_configs (
    id            BIGSERIAL PRIMARY KEY,
    domain        VARCHAR(255) NOT NULL,
    industry      VARCHAR(255) NOT NULL,
    job_role      VARCHAR(255) NOT NULL,
    keywords      TEXT,
    domain_rules  TEXT,
    role_prompt   TEXT,
    system_prompt TEXT NOT NULL,
    media_url     VARCHAR(500),
    is_active     BOOLEAN DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_expert_prompt_configs_job_role_industry_domain ON expert_prompt_configs(job_role, industry, domain);

-- Taxonomy entries
CREATE TABLE taxonomy_entries (
    id         BIGSERIAL PRIMARY KEY,
    domain     VARCHAR(100),
    role       VARCHAR(150),
    industry   VARCHAR(150),
    keywords   TEXT,
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_taxonomy_entries_domain ON taxonomy_entries(domain);
CREATE INDEX idx_taxonomy_entries_role ON taxonomy_entries(role);
CREATE INDEX idx_taxonomy_entries_active ON taxonomy_entries(active);

-- AI knowledge documents
CREATE TABLE ai_knowledge_documents (
    id                  BIGSERIAL PRIMARY KEY,
    media_id            BIGINT NOT NULL,
    title               VARCHAR(255) NOT NULL,
    description         TEXT,
    use_case            VARCHAR(50) NOT NULL,
    approval_status     VARCHAR(20) NOT NULL,
    ingestion_status    VARCHAR(20) NOT NULL,
    uploaded_by_user_id BIGINT NOT NULL,
    approved_by_user_id BIGINT,
    mentor_id           BIGINT,
    skill_name          VARCHAR(255),
    skill_slug          VARCHAR(255),
    industry            VARCHAR(100),
    level               VARCHAR(100),
    course_id           BIGINT,
    module_id           BIGINT,
    assignment_id       BIGINT,
    doc_type            VARCHAR(20) NOT NULL,
    rag_doc_id          VARCHAR(100) UNIQUE,
    mime_type           VARCHAR(255) NOT NULL,
    file_size_bytes     BIGINT NOT NULL,
    original_file_name  VARCHAR(500) NOT NULL,
    storage_folder      VARCHAR(1000) NOT NULL,
    storage_url         VARCHAR(2000) NOT NULL,
    extracted_text      TEXT,
    extract_error       TEXT,
    review_note         TEXT,
    approved_at         TIMESTAMP,
    indexed_at          TIMESTAMP,
    archived_at         TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_knowledge_documents_media_id ON ai_knowledge_documents(media_id);
CREATE INDEX idx_ai_knowledge_documents_uploaded_by ON ai_knowledge_documents(uploaded_by_user_id);
CREATE INDEX idx_ai_knowledge_documents_approval_status ON ai_knowledge_documents(approval_status);
CREATE INDEX idx_ai_knowledge_documents_ingestion_status ON ai_knowledge_documents(ingestion_status);
CREATE INDEX idx_ai_knowledge_documents_use_case ON ai_knowledge_documents(use_case);
CREATE INDEX idx_ai_knowledge_documents_mentor_id ON ai_knowledge_documents(mentor_id);
CREATE INDEX idx_ai_knowledge_documents_skill_slug ON ai_knowledge_documents(skill_slug);

-- ============================================================
-- SECTION 11: JOURNEY SERVICE
-- ============================================================

-- Journeys
CREATE TABLE journeys (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type                 VARCHAR(20),
    domain               VARCHAR(255) NOT NULL,
    title                VARCHAR(255) NOT NULL DEFAULT 'Untitled Journey',
    sub_category         VARCHAR(100),
    industry             VARCHAR(255),
    job_role             VARCHAR(100),
    goal                 VARCHAR(100),
    status               VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED',
    current_level        VARCHAR(20),
    assessment_data      JSONB,
    roadmap_session_id   BIGINT,
    progress_percentage  INTEGER DEFAULT 0,
    ai_summary_report    TEXT,
    started_at           TIMESTAMP WITH TIME ZONE,
    completed_at         TIMESTAMP WITH TIME ZONE,
    last_activity_at     TIMESTAMP WITH TIME ZONE,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_journeys_user_id ON journeys(user_id);
CREATE INDEX idx_journeys_status ON journeys(status);
CREATE INDEX idx_journeys_created_at ON journeys(created_at);

-- Verified skills shown on portfolio and mentorship cards
CREATE TABLE user_verified_skills (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    skill_name            VARCHAR(100) NOT NULL,
    verified_by_mentor_id BIGINT NOT NULL REFERENCES users(id),
    journey_id            BIGINT REFERENCES journeys(id) ON DELETE SET NULL,
    booking_id            BIGINT,
    skill_level           VARCHAR(20),
    verification_note     TEXT,
    featured_order        INTEGER,
    verified_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_verified_skill UNIQUE (user_id, skill_name)
);

CREATE INDEX idx_uvs_user_verified_at ON user_verified_skills(user_id, verified_at DESC);
CREATE INDEX idx_uvs_user_featured_order ON user_verified_skills(user_id, featured_order, verified_at DESC);

-- Roadmap follow-up meetings
CREATE TABLE roadmap_follow_up_meetings (
    id                 BIGSERIAL PRIMARY KEY,
    booking_id         BIGINT NOT NULL,
    journey_id         BIGINT NOT NULL,
    mentor_id          BIGINT NOT NULL,
    learner_id         BIGINT NOT NULL,
    title              VARCHAR(255) NOT NULL,
    agenda             TEXT,
    scheduled_at       TIMESTAMP NOT NULL,
    duration_minutes   INTEGER NOT NULL,
    meeting_link       VARCHAR(1000),
    status             VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED',
    notes              TEXT,
    purpose            VARCHAR(500),
    created_by_role    VARCHAR(20),
    created_by_user_id BIGINT,
    accepted_at        TIMESTAMP,
    rejected_at        TIMESTAMP,
    reject_reason      VARCHAR(500),
    created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_roadmap_follow_up_meetings_booking_id ON roadmap_follow_up_meetings(booking_id);
CREATE INDEX idx_roadmap_follow_up_meetings_journey_id ON roadmap_follow_up_meetings(journey_id);
CREATE INDEX idx_roadmap_follow_up_meetings_mentor_scheduled ON roadmap_follow_up_meetings(mentor_id, scheduled_at);

-- Journey progress
CREATE TABLE journey_progress (
    id                 BIGSERIAL PRIMARY KEY,
    journey_id         BIGINT NOT NULL REFERENCES journeys(id) ON DELETE CASCADE,
    user_id            BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    milestone          VARCHAR(50) NOT NULL,
    is_completed       BOOLEAN NOT NULL DEFAULT FALSE,
    milestone_progress INTEGER DEFAULT 0,
    notes              TEXT,
    completed_at       TIMESTAMP WITH TIME ZONE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_journey_progress_journey_id ON journey_progress(journey_id);
CREATE INDEX idx_journey_progress_user_id ON journey_progress(user_id);
CREATE INDEX idx_journey_progress_milestone ON journey_progress(milestone);

-- Assessment tests
CREATE TABLE assessment_tests (
    id                 BIGSERIAL PRIMARY KEY,
    journey_id         BIGINT NOT NULL REFERENCES journeys(id) ON DELETE CASCADE,
    question_bank_id   BIGINT,
    title              VARCHAR(255) NOT NULL,
    description        TEXT,
    target_field       VARCHAR(100),
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    question_count     INTEGER,
    time_limit_minutes INTEGER,
    difficulty_level   VARCHAR(20),
    assessment_phase   VARCHAR(30),
    base_level         VARCHAR(20),
    tested_level       VARCHAR(20),
    parent_test_id     BIGINT,
    question_source    VARCHAR(30),
    questions_json     JSONB,
    generation_prompt  TEXT,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_assessment_tests_journey_id ON assessment_tests(journey_id);
CREATE INDEX idx_assessment_tests_status ON assessment_tests(status);
CREATE INDEX idx_assessment_tests_question_bank_id ON assessment_tests(question_bank_id);

-- Test results
CREATE TABLE test_results (
    id                      BIGSERIAL PRIMARY KEY,
    journey_id              BIGINT NOT NULL REFERENCES journeys(id) ON DELETE CASCADE,
    assessment_test_id     BIGINT NOT NULL REFERENCES assessment_tests(id) ON DELETE CASCADE,
    score_percentage       INTEGER,
    evaluated_level        VARCHAR(20),
    skill_gaps_json        JSONB,
    strengths_json         JSONB,
    evaluation_summary     TEXT,
    detailed_feedback      TEXT,
    highlight_keywords_json JSONB,
    user_answers_json      JSONB,
    correct_answers_json   JSONB,
    evaluated_at           TIMESTAMP WITH TIME ZONE,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_test_results_journey_id ON test_results(journey_id);
CREATE INDEX idx_test_results_assessment_test_id ON test_results(assessment_test_id);

-- ============================================================
-- SECTION 12: COMMUNITY SERVICE
-- ============================================================

-- Posts
CREATE TABLE posts (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title         VARCHAR(255) NOT NULL,
    content       TEXT NOT NULL,
    thumbnail_url VARCHAR(255),
    tags          VARCHAR(255),
    category      VARCHAR(255),
    status        VARCHAR(20) NOT NULL,
    like_count    INTEGER NOT NULL DEFAULT 0,
    dislike_count INTEGER NOT NULL DEFAULT 0,
    comment_count INTEGER NOT NULL DEFAULT 0,
    view_count    INTEGER NOT NULL DEFAULT 0,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_posts_user_id ON posts(user_id);
CREATE INDEX idx_posts_status ON posts(status);
CREATE INDEX idx_posts_created_at ON posts(created_at);

-- Comments
CREATE TABLE comments (
    id               BIGSERIAL PRIMARY KEY,
    post_id          BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id          BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content          TEXT NOT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    parent_id        BIGINT,
    hidden           BOOLEAN NOT NULL DEFAULT FALSE,
    moderation_note  VARCHAR(255),
    report_count     INTEGER NOT NULL DEFAULT 0,
    last_report_reason VARCHAR(255)
);

CREATE INDEX idx_comments_post_id ON comments(post_id);
CREATE INDEX idx_comments_parent_id ON comments(parent_id);
CREATE INDEX idx_comments_user_id ON comments(user_id);

-- Post likes
CREATE TABLE post_likes (
    id         BIGSERIAL PRIMARY KEY,
    post_id    BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_post_like_user UNIQUE (post_id, user_id)
);

CREATE INDEX idx_post_likes_post_id ON post_likes(post_id);
CREATE INDEX idx_post_likes_user_id ON post_likes(user_id);

-- Post dislikes
CREATE TABLE post_dislikes (
    id         BIGSERIAL PRIMARY KEY,
    post_id    BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_post_dislike_user UNIQUE (post_id, user_id)
);

CREATE INDEX idx_post_dislikes_post_id ON post_dislikes(post_id);
CREATE INDEX idx_post_dislikes_user_id ON post_dislikes(user_id);

-- Saved posts
CREATE TABLE saved_posts (
    id         BIGSERIAL PRIMARY KEY,
    post_id    BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_saved_post_user UNIQUE (post_id, user_id)
);

CREATE INDEX idx_saved_posts_post_id ON saved_posts(post_id);
CREATE INDEX idx_saved_posts_user_id ON saved_posts(user_id);

-- ============================================================
-- SECTION 13: STUDY SERVICE
-- ============================================================

-- Study sessions
CREATE TABLE study_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(255),
    start_time      TIMESTAMP,
    end_time        TIMESTAMP,
    status          VARCHAR(20),
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    full_description TEXT
);

CREATE INDEX idx_study_sessions_user_id ON study_sessions(user_id);
CREATE INDEX idx_study_sessions_status ON study_sessions(status);

-- Dashboard notes
CREATE TABLE dashboard_notes (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content    TEXT,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_dashboard_notes_user_id ON dashboard_notes(user_id);

-- Task columns
CREATE TABLE task_columns (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255),
    order_index INTEGER,
    color       VARCHAR(255),
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_task_columns_user_id ON task_columns(user_id);

-- Tasks
CREATE TABLE tasks (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title              VARCHAR(255),
    full_description   TEXT,
    start_date         TIMESTAMP,
    end_date           TIMESTAMP,
    deadline           TIMESTAMP,
    priority           VARCHAR(20),
    order_index        DOUBLE PRECISION,
    status             VARCHAR(255),
    user_progress      INTEGER,
    satisfaction_level VARCHAR(255),
    user_notes         TEXT,
    column_id          UUID REFERENCES task_columns(id) ON DELETE SET NULL,
    user_id            BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    archived           BOOLEAN DEFAULT FALSE
);

CREATE INDEX idx_tasks_user_id ON tasks(user_id);
CREATE INDEX idx_tasks_column_id ON tasks(column_id);
CREATE INDEX idx_tasks_status ON tasks(status);
CREATE INDEX idx_tasks_priority ON tasks(priority);

-- Task study sessions (many-to-many join table)
CREATE TABLE task_study_sessions (
    task_id    UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    session_id UUID NOT NULL REFERENCES study_sessions(id) ON DELETE CASCADE,
    PRIMARY KEY (task_id, session_id)
);

-- ============================================================
-- SECTION 14: NOTIFICATION SERVICE
-- ============================================================

-- Notifications
CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title      VARCHAR(255) NOT NULL,
    message    VARCHAR(255) NOT NULL,
    type       VARCHAR(50) NOT NULL,
    is_read    BOOLEAN NOT NULL DEFAULT FALSE,
    related_id VARCHAR(255),
    sender_id  BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_type ON notifications(type);
CREATE INDEX idx_notifications_is_read ON notifications(is_read);
CREATE INDEX idx_notifications_created_at ON notifications(created_at);

-- User FCM tokens
CREATE TABLE user_fcm_tokens (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_token VARCHAR(500) NOT NULL,
    device_type  VARCHAR(20) NOT NULL DEFAULT 'ANDROID',
    device_name  VARCHAR(100),
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP
);

CREATE INDEX idx_user_fcm_tokens_user_id ON user_fcm_tokens(user_id);
CREATE INDEX idx_user_fcm_tokens_active ON user_fcm_tokens(active);

-- ============================================================
-- SECTION 15: CONTENT SERVICE
-- ============================================================

-- Sliders
CREATE TABLE sliders (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title          VARCHAR(255) NOT NULL,
    description    TEXT,
    image_url      VARCHAR(255) NOT NULL,
    public_id      VARCHAR(255),
    cta_text       VARCHAR(255),
    cta_link       VARCHAR(255),
    is_active      BOOLEAN DEFAULT TRUE,
    is_login       BOOLEAN DEFAULT FALSE,
    display_order INTEGER,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- SECTION 16: SUPPORT SERVICE
-- ============================================================

-- Support tickets
CREATE TABLE support_tickets (
    id            BIGSERIAL PRIMARY KEY,
    ticket_code   VARCHAR(20) NOT NULL UNIQUE,
    user_id       BIGINT REFERENCES users(id) ON DELETE SET NULL,
    email         VARCHAR(255) NOT NULL,
    subject       VARCHAR(255) NOT NULL,
    category      VARCHAR(50) NOT NULL,
    priority      VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    description   TEXT NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    admin_response TEXT,
    assigned_to   BIGINT,
    resolved_at   TIMESTAMP,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_support_tickets_user_id ON support_tickets(user_id);
CREATE INDEX idx_support_tickets_status ON support_tickets(status);
CREATE INDEX idx_support_tickets_priority ON support_tickets(priority);
CREATE INDEX idx_support_tickets_created_at ON support_tickets(created_at);

-- Ticket messages
CREATE TABLE ticket_messages (
    id           BIGSERIAL PRIMARY KEY,
    ticket_id    BIGINT NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    sender_id    BIGINT REFERENCES users(id) ON DELETE SET NULL,
    sender_email VARCHAR(255),
    sender_name  VARCHAR(255),
    sender_type  VARCHAR(20) NOT NULL,
    content      TEXT NOT NULL,
    is_read      BOOLEAN DEFAULT FALSE,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ticket_messages_ticket_id ON ticket_messages(ticket_id);
CREATE INDEX idx_ticket_messages_sender_id ON ticket_messages(sender_id);

-- ============================================================
-- SECTION 17: REPORT SERVICE
-- ============================================================

-- Violation reports
CREATE TABLE violation_reports (
    id                   BIGSERIAL PRIMARY KEY,
    report_code          VARCHAR(20) NOT NULL UNIQUE,
    title                VARCHAR(255) NOT NULL,
    reporter_id          BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reported_user_id     BIGINT REFERENCES users(id) ON DELETE SET NULL,
    reported_user_name   VARCHAR(100),
    report_type          VARCHAR(50) NOT NULL,
    severity             VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    description          TEXT NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    admin_notes          TEXT,
    assigned_admin_id    BIGINT,
    resolution_action    VARCHAR(50),
    resolved_at          TIMESTAMP,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_violation_reports_reporter_id ON violation_reports(reporter_id);
CREATE INDEX idx_violation_reports_reported_user_id ON violation_reports(reported_user_id);
CREATE INDEX idx_violation_reports_status ON violation_reports(status);
CREATE INDEX idx_violation_reports_created_at ON violation_reports(created_at);

-- Report evidences
CREATE TABLE report_evidences (
    id                  BIGSERIAL PRIMARY KEY,
    violation_report_id BIGINT NOT NULL REFERENCES violation_reports(id) ON DELETE CASCADE,
    evidence_type       VARCHAR(20) NOT NULL,
    file_url            VARCHAR(255),
    file_name           VARCHAR(255),
    description         TEXT,
    external_link       VARCHAR(255),
    file_size           BIGINT,
    mime_type           VARCHAR(100),
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_report_evidences_violation_report_id ON report_evidences(violation_report_id);

-- ============================================================
-- SECTION 18: SKIN SERVICE
-- ============================================================

-- Meowl skins
CREATE TABLE meowl_skins (
    id         BIGSERIAL PRIMARY KEY,
    skin_code  VARCHAR(255) NOT NULL UNIQUE,
    name       VARCHAR(255) NOT NULL,
    name_vi    VARCHAR(255) NOT NULL,
    image_url  VARCHAR(255) NOT NULL,
    is_premium BOOLEAN NOT NULL DEFAULT FALSE,
    price      NUMERIC(10,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- User skins
CREATE TABLE user_skins (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    skin_id      BIGINT NOT NULL REFERENCES meowl_skins(id) ON DELETE CASCADE,
    purchased_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active    BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_user_skins_user_id ON user_skins(user_id);
CREATE INDEX idx_user_skins_skin_id ON user_skins(skin_id);

-- ============================================================
-- SECTION 19: PRE-CHAT SERVICE
-- ============================================================

-- Pre-chat blocks
CREATE TABLE prechat_blocks (
    id         BIGSERIAL PRIMARY KEY,
    mentor_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    learner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_prechat_blocks_mentor_learner UNIQUE (mentor_id, learner_id)
);

CREATE INDEX idx_prechat_blocks_mentor_id ON prechat_blocks(mentor_id);
CREATE INDEX idx_prechat_blocks_learner_id ON prechat_blocks(learner_id);

-- Pre-chat messages
CREATE TABLE prechat_messages (
    id             BIGSERIAL PRIMARY KEY,
    mentor_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    learner_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sender_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content        VARCHAR(1000) NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_by_mentor  BOOLEAN NOT NULL DEFAULT FALSE,
    read_by_learner BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_prechat_messages_lookup ON prechat_messages(mentor_id, learner_id, created_at);

-- Pre-chat reports
CREATE TABLE prechat_reports (
    id         BIGSERIAL PRIMARY KEY,
    mentor_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    learner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reporter_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message_id BIGINT,
    reason     TEXT NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_prechat_reports_mentor_id ON prechat_reports(mentor_id);
CREATE INDEX idx_prechat_reports_learner_id ON prechat_reports(learner_id);

-- Pre-chat thread state
CREATE TABLE prechat_thread_state (
    id                  BIGSERIAL PRIMARY KEY,
    mentor_id           BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    learner_id          BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    hidden_for_mentor    BOOLEAN NOT NULL DEFAULT FALSE,
    hidden_for_learner   BOOLEAN NOT NULL DEFAULT FALSE,
    muted_for_mentor    BOOLEAN NOT NULL DEFAULT FALSE,
    muted_for_learner   BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_prechat_thread_mentor_learner UNIQUE (mentor_id, learner_id)
);

-- ============================================================
-- SECTION 20: MEOWL CHAT SERVICE
-- ============================================================

-- Meowl chat messages
CREATE TABLE meowl_chat_messages (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role         VARCHAR(20) NOT NULL,
    content      TEXT NOT NULL,
    active_role  VARCHAR(20),
    session_id   VARCHAR(100),
    message_type VARCHAR(30) NOT NULL DEFAULT 'CHAT',
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_meowl_chat_messages_user_id ON meowl_chat_messages(user_id);
CREATE INDEX idx_meowl_chat_messages_session_id ON meowl_chat_messages(session_id);
CREATE INDEX idx_meowl_chat_messages_created_at ON meowl_chat_messages(created_at);

-- Meowl user preferences
CREATE TABLE meowl_user_preferences (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    preferred_role_mode VARCHAR(20),
    onboarding_seen     BOOLEAN NOT NULL DEFAULT FALSE,
    onboarding_seen_at TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- SECTION 21: CHAT SERVICE
-- ============================================================

-- Group chats
CREATE TABLE group_chats (
    id        BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    mentor_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name      VARCHAR(255),
    avatar_url VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_group_chats_course_id ON group_chats(course_id);
CREATE INDEX idx_group_chats_mentor_id ON group_chats(mentor_id);

-- Group chat members
CREATE TABLE group_chat_members (
    id        BIGSERIAL PRIMARY KEY,
    group_id  BIGINT NOT NULL REFERENCES group_chats(id) ON DELETE CASCADE,
    user_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role      VARCHAR(20),
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_group_chat_members_group_id ON group_chat_members(group_id);
CREATE INDEX idx_group_chat_members_user_id ON group_chat_members(user_id);

-- Group chat messages
CREATE TABLE group_chat_messages (
    id               BIGSERIAL PRIMARY KEY,
    group_id         BIGINT NOT NULL REFERENCES group_chats(id) ON DELETE CASCADE,
    sender_id        BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sender_name      VARCHAR(255),
    content          TEXT,
    message_type     VARCHAR(20) DEFAULT 'TEXT',
    gif_url          VARCHAR(500),
    image_url        VARCHAR(500),
    emoji_code       VARCHAR(100),
    sender_avatar_url VARCHAR(500),
    timestamp        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_group_chat_messages_group_id ON group_chat_messages(group_id);
CREATE INDEX idx_group_chat_messages_sender_id ON group_chat_messages(sender_id);
CREATE INDEX idx_group_chat_messages_timestamp ON group_chat_messages(timestamp);

-- User chat messages
CREATE TABLE user_chat_messages (
    id              BIGSERIAL PRIMARY KEY,
    sender_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipient_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sender_name     VARCHAR(255),
    recipient_name  VARCHAR(255),
    content         TEXT,
    timestamp       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status          VARCHAR(20)
);

CREATE INDEX idx_user_chat_messages_sender_id ON user_chat_messages(sender_id);
CREATE INDEX idx_user_chat_messages_recipient_id ON user_chat_messages(recipient_id);
CREATE INDEX idx_user_chat_messages_timestamp ON user_chat_messages(timestamp);

-- ============================================================
-- SECTION 22: QUESTION BANK SERVICE
-- ============================================================

-- Question banks
CREATE TABLE question_banks (
    id                       BIGSERIAL PRIMARY KEY,
    domain                   VARCHAR(255) NOT NULL,
    industry                 VARCHAR(255),
    job_role                 VARCHAR(255),
    title                    VARCHAR(255) NOT NULL,
    description              TEXT,
    difficulty_distribution  TEXT,
    is_active                BOOLEAN NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_question_banks_domain ON question_banks(domain);
CREATE INDEX idx_question_banks_job_role ON question_banks(job_role);
CREATE INDEX idx_question_banks_is_active ON question_banks(is_active);

-- Question bank questions
CREATE TABLE question_bank_questions (
    id               BIGSERIAL PRIMARY KEY,
    question_bank_id BIGINT NOT NULL REFERENCES question_banks(id) ON DELETE CASCADE,
    question_text    TEXT NOT NULL,
    options          TEXT,
    correct_answer   VARCHAR(1) NOT NULL,
    explanation      TEXT,
    difficulty       VARCHAR(20) NOT NULL,
    skill_area       VARCHAR(255),
    category         VARCHAR(255),
    source           VARCHAR(255),
    used_count       INTEGER DEFAULT 0,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_question_bank_questions_bank_id ON question_bank_questions(question_bank_id);
CREATE INDEX idx_question_bank_questions_difficulty ON question_bank_questions(difficulty);
CREATE INDEX idx_question_bank_questions_is_active ON question_bank_questions(is_active);

-- ============================================================
-- SECTION 23: STUDENT LEARNING REPORT SERVICE
-- ============================================================

-- Student learning reports
CREATE TABLE student_learning_reports (
    id                            BIGSERIAL PRIMARY KEY,
    student_id                    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    student_name                  VARCHAR(255) NOT NULL,
    report_content                TEXT,
    current_skills_section        TEXT,
    learning_goals_section        TEXT,
    progress_section              TEXT,
    strengths_section             TEXT,
    areas_to_improve_section      TEXT,
    recommendations_section       TEXT,
    skill_gaps_section            TEXT,
    next_steps_section            TEXT,
    motivation_section            TEXT,
    generated_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_ai_generated               BOOLEAN DEFAULT TRUE,
    report_type                   VARCHAR(30) NOT NULL DEFAULT 'COMPREHENSIVE',
    average_progress_snapshot      INTEGER,
    learning_trend                VARCHAR(20),
    recommended_focus             TEXT,
    total_study_hours_snapshot    INTEGER,
    streak_days_snapshot          INTEGER,
    tasks_completed_snapshot      INTEGER
);

CREATE INDEX idx_student_learning_reports_student_id ON student_learning_reports(student_id, generated_at DESC);
CREATE INDEX idx_student_learning_reports_type ON student_learning_reports(report_type);

-- ============================================================
-- SECTION 24: MISCELLANEOUS CONSTRAINTS & SEQUENCES
-- ============================================================

-- Ensure BIGSERIAL sequences start at a reasonable value
-- (Run these if seeding data, otherwise default 1 is fine)
-- SELECT setval(pg_get_serial_sequence('users', 'id'), 1, false);
-- SELECT setval(pg_get_serial_sequence('roles', 'id'), 1, false);

-- ============================================================
-- END OF SCHEMA
-- ============================================================
