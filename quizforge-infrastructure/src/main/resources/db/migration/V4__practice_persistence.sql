-- Durable practice facts live in the application DB, not the rebuildable workspace asset index.
CREATE TABLE practice_session (
    id TEXT NOT NULL PRIMARY KEY CHECK (length(trim(id)) > 0),
    question_bank_asset_id TEXT NOT NULL CHECK (length(trim(question_bank_asset_id)) > 0),
    question_bank_content_id TEXT NOT NULL CHECK (length(trim(question_bank_content_id)) > 0),
    bank_title_snapshot TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    current_view TEXT NOT NULL CHECK (current_view IN ('QUESTION', 'SUMMARY')),
    current_question_id TEXT,
    started_at TEXT NOT NULL,
    last_activity_at TEXT NOT NULL,
    archived_at TEXT,
    CHECK (current_view = 'SUMMARY' OR
        (current_question_id IS NOT NULL AND length(trim(current_question_id)) > 0)),
    CHECK ((status = 'ACTIVE' AND archived_at IS NULL) OR
        (status = 'ARCHIVED' AND archived_at IS NOT NULL))
);

CREATE UNIQUE INDEX uq_practice_session_active_bank
    ON practice_session(question_bank_asset_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_practice_session_bank_status
    ON practice_session(question_bank_asset_id, status);

CREATE TABLE practice_session_question (
    id TEXT NOT NULL PRIMARY KEY CHECK (length(trim(id)) > 0),
    session_id TEXT NOT NULL,
    question_id TEXT NOT NULL CHECK (length(trim(question_id)) > 0),
    question_order INTEGER NOT NULL CHECK (question_order >= 0),
    -- Open string for future question types such as SHORT_ANSWER.
    question_type TEXT NOT NULL CHECK (length(trim(question_type)) > 0),
    stem_snapshot TEXT NOT NULL,
    options_snapshot_json TEXT NOT NULL,
    correct_answer_snapshot_json TEXT NOT NULL,
    analysis_snapshot TEXT NOT NULL,
    source_refs_snapshot_json TEXT NOT NULL,
    practice_state TEXT NOT NULL CHECK (practice_state IN
        ('UNANSWERED', 'DRAFT', 'SUBMITTED', 'RETRYING', 'REVISING')),
    draft_answer_json TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (session_id) REFERENCES practice_session(id) ON DELETE CASCADE,
    UNIQUE (session_id, question_id)
);

CREATE INDEX idx_practice_session_question_order
    ON practice_session_question(session_id, question_order);

CREATE TABLE question_attempt (
    id TEXT NOT NULL PRIMARY KEY CHECK (length(trim(id)) > 0),
    session_question_id TEXT NOT NULL,
    attempt_no INTEGER NOT NULL CHECK (attempt_no >= 1),
    attempt_mode TEXT NOT NULL CHECK (attempt_mode IN ('INITIAL', 'REVISION', 'RETRY')),
    answer_json TEXT NOT NULL,
    result TEXT NOT NULL CHECK (result IN ('CORRECT', 'INCORRECT', 'UNSCORED')),
    score REAL,
    max_score REAL,
    submitted_at TEXT NOT NULL,
    FOREIGN KEY (session_question_id) REFERENCES practice_session_question(id) ON DELETE CASCADE,
    UNIQUE (session_question_id, attempt_no)
);
