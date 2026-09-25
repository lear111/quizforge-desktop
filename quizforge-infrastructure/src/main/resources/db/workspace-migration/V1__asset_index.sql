CREATE TABLE asset_index (
    asset_id TEXT PRIMARY KEY,
    asset_type TEXT NOT NULL CHECK (asset_type IN ('STANDARD_DOCUMENT', 'QUESTION_BANK')),
    current_path TEXT NOT NULL,
    title TEXT NOT NULL,
    indexed_at TEXT NOT NULL
);
