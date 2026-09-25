CREATE TABLE workspace (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL CHECK (length(trim(name)) > 0),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE material (
    id TEXT PRIMARY KEY,
    workspace_id TEXT NOT NULL,
    original_file_name TEXT NOT NULL,
    stored_file_name TEXT NOT NULL,
    file_size INTEGER NOT NULL CHECK (file_size > 0),
    status TEXT NOT NULL CHECK (status = 'IMPORTED'),
    created_at TEXT NOT NULL,
    FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE RESTRICT
);

CREATE INDEX idx_material_workspace_id ON material(workspace_id);
