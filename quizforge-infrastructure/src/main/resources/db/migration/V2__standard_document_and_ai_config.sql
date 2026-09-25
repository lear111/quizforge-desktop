CREATE TABLE ai_provider_config (
    id TEXT PRIMARY KEY,
    provider_type TEXT NOT NULL,
    base_url TEXT NOT NULL,
    model TEXT NOT NULL,
    credential_ref TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE standard_document (
    id TEXT PRIMARY KEY,
    workspace_id TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    format_id TEXT NOT NULL,
    format_version TEXT NOT NULL,
    file_name TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status = 'VALID'),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE RESTRICT
);

CREATE TABLE standard_document_material (
    standard_document_id TEXT NOT NULL,
    material_id TEXT NOT NULL,
    PRIMARY KEY (standard_document_id, material_id),
    FOREIGN KEY (standard_document_id) REFERENCES standard_document(id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(id) ON DELETE RESTRICT
);
