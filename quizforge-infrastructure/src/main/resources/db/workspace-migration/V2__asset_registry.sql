CREATE TABLE asset_registry (
    asset_id TEXT PRIMARY KEY,
    asset_type TEXT NOT NULL CHECK (asset_type IN ('STANDARD_DOCUMENT', 'QUESTION_BANK')),
    content_id TEXT,
    current_path TEXT NOT NULL,
    title TEXT NOT NULL,
    schema_version TEXT NOT NULL,
    indexed_at TEXT NOT NULL
);
