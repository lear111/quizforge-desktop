-- Versions belong to document_json, the sole geometry authority.
CREATE TABLE practice_draft_canvas (
    session_question_id TEXT NOT NULL PRIMARY KEY,
    document_json TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (session_question_id) REFERENCES practice_session_question(id) ON DELETE CASCADE
);

CREATE TABLE attempt_draft_snapshot (
    attempt_id TEXT NOT NULL PRIMARY KEY,
    document_json TEXT NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY (attempt_id) REFERENCES question_attempt(id) ON DELETE CASCADE
);

-- Defend immutability against accidental SQL updates as well as the append-only port.
CREATE TRIGGER attempt_draft_snapshot_no_update
BEFORE UPDATE ON attempt_draft_snapshot
BEGIN
    SELECT RAISE(ABORT, 'Attempt DraftSnapshot is immutable');
END;
