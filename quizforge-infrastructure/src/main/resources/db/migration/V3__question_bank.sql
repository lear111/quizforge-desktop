CREATE TABLE question_bank (
    id TEXT PRIMARY KEY,
    workspace_id TEXT NOT NULL UNIQUE,
    source_document_id TEXT NOT NULL,
    name TEXT NOT NULL,
    generation_scope_type TEXT NOT NULL CHECK (generation_scope_type IN ('DOCUMENT', 'CHAPTER', 'SECTION')),
    source_chapter TEXT NOT NULL,
    source_section TEXT NOT NULL,
    requested_question_count INTEGER NOT NULL CHECK (requested_question_count BETWEEN 1 AND 50),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    generated_at TEXT NOT NULL,
    FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE,
    FOREIGN KEY (source_document_id) REFERENCES standard_document(id) ON DELETE CASCADE
);

CREATE TABLE question (
    id TEXT PRIMARY KEY,
    question_bank_id TEXT NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('SINGLE_CHOICE', 'MULTIPLE_CHOICE')),
    stem TEXT NOT NULL,
    analysis TEXT NOT NULL,
    source_chapter TEXT NOT NULL,
    source_section TEXT NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY (question_bank_id) REFERENCES question_bank(id) ON DELETE CASCADE
);

CREATE TABLE question_option (
    id TEXT PRIMARY KEY,
    question_id TEXT NOT NULL,
    option_key TEXT NOT NULL,
    content TEXT NOT NULL,
    correct INTEGER NOT NULL CHECK (correct IN (0, 1)),
    sort_order INTEGER NOT NULL,
    FOREIGN KEY (question_id) REFERENCES question(id) ON DELETE CASCADE
);

CREATE INDEX idx_question_bank_order ON question(question_bank_id, sort_order);
CREATE INDEX idx_question_option_order ON question_option(question_id, sort_order);
