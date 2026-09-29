# QuizForge Desktop V2

Independent Java 21 Maven desktop project. It does not use QuizForge V1 code or start a web server.

## Module boundaries

| Module | Responsibility | Direct project dependencies |
| --- | --- | --- |
| `quizforge-extension-api` | Vendor neutral AI, document structure and question generation contracts | None |
| `quizforge-core` | Workspace, Asset, Material, StandardDocument and QuestionBank models, services and ports | `quizforge-extension-api` |
| `quizforge-default-extensions` | DeepSeek, Standard Markdown v1 and choice question generation | `quizforge-extension-api` |
| `quizforge-infrastructure` | SQLite, Flyway, local files and Windows DPAPI credentials | `quizforge-core` |
| `quizforge-desktop-app` | JavaFX UI and Spring composition root | All four modules |

## Build and run

Use Maven with JDK 21 from this directory:

```powershell
mvn test
mvn install -DskipTests
mvn -pl quizforge-desktop-app javafx:run
```

The app creates `%USERPROFILE%\.quizforge\quizforge.db`. Imported materials are stored
under `workspaces\{workspace-id}\materials\`. Validated Standard Documents are stored
at `workspaces\{workspace-id}\document\study.md`. SQLite stores metadata and Material
provenance. AI provider settings contain a credential reference; API keys are encrypted
with Windows DPAPI in `secrets\` and never stored in SQLite.

## File-first workspace foundation

New workspaces are real directories under `%USERPROFILE%\.quizforge\workspaces\{workspace-id}\`
(or under the overridden data directory). Each new root includes `sources/`, `documents/`,
`question-banks/`, and `.quizforge/`. The internal directory contains `workspace.json`
with a stable workspace UUID and `workspace.db` with the rebuildable `asset_registry` table.
The old `materials/` and `document/` paths remain in use by the existing generation flow.
The global `quizforge.db` remains in use by the current MVP.
Existing workspaces receive the missing directories and internal metadata when loaded;
their legacy materials and generated documents stay in place.

The workspace scanner recursively checks arbitrary folders except `.quizforge/`.
The default folders are optional categories, never asset-type rules. A valid file-backed
Standard Knowledge Document is a `.md` file with `quizforge_format: study-document`,
`schema_version: "1.0"`, a stable `quizforge_id`, `title`, and `language` in YAML Front Matter.
Its body has exactly one H1, at least one H2 with a `<!-- qf:id=chapter_x -->` comment,
and at least one H3 with a `<!-- qf:id=section_x -->` comment and nonempty body per chapter.
IDs must be unique in the document. The former AI-generated Draft is still stored and used
by the existing generation pipeline, but is not recognized as a file-backed v1 asset.

The document `contentId` is `qfd:v1:` plus lowercase SHA-256 over a canonical sequence:
UTF-8 domain separator `quizforge-study-document-canonical-v1` plus NUL, then four
length-prefixed UTF-8 fields in order: schema version, title, language, complete Markdown
body. Each length is a 32-bit big-endian byte count. Line endings become LF and Unicode is
NFC normalized; title and language are trimmed. The body includes headings, chapter and
section ID comments, prose, lists, and code. Front Matter `quizforge_id`, file path/name,
mtime, UI state, cache, and database values are excluded. The hash is computed on scan and
is never written into Markdown.

A `.qbank` is a QBank v2 JSON document with `schemaVersion: "2.0"`, stable `assetId`,
`title`, `stimuli`, `questions`, and `resources`. Valid logical content has a `qfb:v2:`
SHA-256 revision independent of asset identity, filesystem path and UI state.
QBank v1 files are not accepted. See [QBank v2 foundation](docs/qbank-v2-foundation.md)
and the JSON Schema at `quizforge-infrastructure/src/main/resources/schema/qbank-v2.schema.json`.
The registry records only normalized workspace-relative paths. A rescan updates paths for
moved or renamed assets, updates content IDs after edits, and removes entries for deleted
files. Duplicate asset IDs are reported as `DUPLICATE_ASSET_ID` and neither conflicting file
is indexed. Invalid files are reported and skipped. Removing `workspace.db` and scanning
again rebuilds the registry from files.
The scanner indexes files; this step does not write `.qbank` files or move existing assets.

The Standard Document page now creates real file-backed v1 documents. The existing AI
processor supplies a validated Draft with semantic headings and content; the local
assembler discards any AI-provided QuizForge IDs, creates document/chapter/section IDs,
and validates the completed file with the same v1 parser used by the scanner. New files
use a title-based name under `documents/`; collisions receive ` (2)`, ` (3)`, etc.
Regenerating a selected file retains its asset ID, writes via a staged replacement,
and refreshes the registry immediately. The page reads its preview from the saved `.md`
file and shows the asset ID, content ID, and workspace-relative path. Multiple document
files can coexist and be rediscovered after restart or relocation.
If registry refresh fails after a new file is published, the valid file remains on disk
and the error names its relative path; a later scan can register it. A failed regeneration
restores the previous file.

The old `standard_document` tables and `document/study.md` Draft path remain solely for
the current QuestionBank compatibility flow. New document generation does not write to
those tables or treat them as the source of its formal content. QuestionBank generation
from file-backed documents is a later migration step.

The Question Bank tab generates 1–50 single and/or multiple choice questions from the whole
Standard Document, one chapter or one section. The dialog populates chapter and section
choices from CommonMark headings. AI output must be JSON. The local validator accepts only
questions with valid options, correct answers and source labels. Invalid candidates are
discarded; at least one valid question is required. SQLite V3 stores one current bank per
workspace and replaces it in one transaction after generation completes. A failed generation
or save retains the previous bank. The page warns when its source document has changed.

Override the data directory for an isolated run with the JVM property `quizforge.dataDir`.
For example, in PowerShell:

```powershell
$env:JAVA_TOOL_OPTIONS = '-Dquizforge.dataDir=C:\temp\quizforge-demo'
mvn -pl quizforge-desktop-app javafx:run
Remove-Item Env:JAVA_TOOL_OPTIONS
```

In AI Settings, save DeepSeek Base URL, Model and your own API key, then use Test Connection.
Defaults are `https://api.deepseek.com` and `deepseek-v4-flash`. Network calls run in
background JavaFX tasks. Candidate documents are saved only after local Standard Markdown
v1 validation. A failed regeneration keeps the previous document.

`quizforge.material.maxBytes` defaults to 10 MiB per imported Markdown material.
`quizforge.document.maxInputChars` defaults to 100,000 characters across selected Material
names and content. Oversized inputs are rejected without truncation.

`mvn test` uses fake providers and a local mock HTTP server; it never contacts the live
DeepSeek API. Live acceptance requires a user supplied key entered locally in AI Settings.
