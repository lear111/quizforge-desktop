# QBank v2: logical format and ZIP package

The current `.qbank` is a ZIP-compatible package. Only `schemaVersion: "2.0"` is
accepted. There is no plain JSON or v1 reader, fallback, or production migration.
Existing user workspaces are never rewritten. The logical domain now also supports
ESSAY; the JSON codec serves internal serialization and logical hashing only.

## Physical package (Step 2)

```text
manifest.json
bank.json
resources/... (optional binary entries)
```

Both JSON entries are required even when resources are empty. The manifest contains
`format: "quizforge-question-bank"`, `schemaVersion`, `assetId`, `title`, and
`resources`. Each resource has `id`, `kind`, `mediaType`, `path`, and `sha256`.
Package `path` maps to domain `locator`; it must be under `resources/`, use `/`, and
have no absolute prefix, drive, backslash, empty segment, `.` or `..` segment.
`bank.json` contains only `stimuli` and `questions`. The reader assembles the single
existing QuestionBank domain and reuses its validator. Optional null normalization
continues to apply inside bank.json.

`QBankPackageReader.read/open` validates all resource hashes with bounded streams.
`inspect` validates entries, metadata and logical content but deliberately does not
inflate resource bytes; Registry revisions use the declared resource hashes.
Opening the file validates actual bytes. An empty, valid draft can be read and
edited, while normal bank validation still rejects it for practice.

`ResourceContentProvider` opens caller-owned streams. `LoadedPackage` owns its ZIP
handle and provides resource streams; close all streams and the package before
replacing a file on Windows. Binary bytes never enter QuestionBank or either JSON.

The writer spools resource streams with size checks, calculates SHA-256 from actual
bytes, serializes canonical JSON, closes a temporary ZIP, re-reads it, and replaces
the target with an atomic move. It fails if atomic replacement is unsupported.
Temporary resources and ZIP files are removed on success or failure. Staged
application saves retain a backup for Registry failure rollback.

Entries are written as manifest, bank, then resource paths in lexicographical order.
Timestamps, compression and entry order do not participate in logical revision
hashing. Resource metadata order and locator retain the Step 1 hash semantics.

Central `PackageLimits` defaults: 10,000 entries; manifest 4 MiB; bank 64 MiB;
each resource 256 MiB; total uncompressed content 1 GiB. Both declared and actual
decompressed sizes are checked. Before ZipFile opens, the ZIP end record limits
entry count and central-directory allocation (at most 64 MiB, scaled down for
smaller entry-count limits). Unsafe/duplicate paths and unlisted file entries
are rejected; no extraction to workspace directories occurs. Manifest identity,
resource IDs, unique paths, kind, media type and lowercase SHA-256 are validated
separately from question business rules. Errors retain package-specific codes.

## Domain

```text
QuestionBank(assetId, title, schemaVersion, stimuli, questions, resources)
├── Stimulus(id, QuestionContent)
├── QBankResource(id, ResourceKind, mediaType, locator, sha256)
└── Question(id, type, stimulusRefs, prompt, payload, answerSpec,
             scoreSpec, evaluationSpec?, analysis?, sourceRefs)
    ├── QuestionContent
    │   ├── TextContent(text)
    │   └── RichContent(RichDocument)
    │       ├── BlockNode: ParagraphNode, BlockImageNode, BlockMathNode
    │       └── InlineNode: InlineTextNode, InlineImageNode, InlineMathNode,
    │                       LineBreakNode, LinkNode
    ├── QuestionPayload → ChoicePayload(options: ChoiceOption[]) / EssayPayload
    ├── QuestionAnswerSpec → ChoiceAnswerSpec(correctOptionIds) / EssayAnswerSpec
    ├── ScoreSpec(defaultMaxScore: BigDecimal)
    ├── EvaluationSpec(criteria, evaluatorGuidance)
    └── SourceRef(documentAssetId, documentContentId, anchorName, occurrence,
                  documentTitle?, sectionTitle?)
```

Both image node contexts reference the same top-level image resource table. Links
have inline children; paragraphs can freely mix text, formulas, images and breaks.
All collections are defensive immutable copies. JSON discriminators are explicit
`kind` / `type` names, never Java class names or enum ordinals. Unknown values fail
clearly rather than being coerced to TEXT.

The existing relational generation records are now `StoredQuestionBank` and
`StoredQuestion`. They still serve the database generation workflow and are not
portable format DTOs. `QuestionSourceDocument` is a derived runtime summary, not a
stored `sourceDocuments` field. The recorded source address type also serves
archived practice snapshots; historical address handling remains independent of
the current QBank reader, which accepts named anchors only.

## Validation

`QuestionBankValidator` owns logical-bank and cross-reference validation. JSON Schema
describes the structure; Java validation additionally checks unique IDs, positive
decimal values, answer cardinality, source revision consistency, existing stimulus
and image resource references, and matching resource kinds. Domain validation only
checks metadata; package opening additionally verifies binary hashes. Locators are relative logical paths: no drive
letters, absolute paths, traversal segments, backslashes, or data/base64 URIs.

SINGLE_CHOICE, MULTIPLE_CHOICE and ESSAY are supported. Single choice requires exactly one
correct option. Multiple choice retains the current rule: at least two correct
options, with at least one incorrect option. Option IDs remain unique bank-wide.
Questions may have no source references. Analysis and evaluation are optional.
TEXT prompts/options cannot be blank; TEXT analysis may be blank or absent.

`ScoreSpec.defaultMaxScore` uses `BigDecimal`, is required and positive. Newly
created questions default to `BigDecimal.ONE`. Editor mutations preserve score,
evaluation, stimuli and resource metadata. ESSAY editing exposes a decimal score control.

Evaluation criterion weights use `BigDecimal`, are positive, and sum exactly to 1
when criteria are nonempty. This gives portable normalized relative weights without
an implicit normalization algorithm or floating point tolerance. Empty criteria
are valid. This step does not introduce runtime evaluation.

## Optional JSON contract (Step 1.1)

Missing and explicit-null optional properties both represent the same absent value
in the logical model. The reader tolerates either form; the canonical writer omits
absent properties and never writes `"field": null`. The same NON_NULL logical tree
is used by contentId, so missing and explicit null produce equal domain models,
equal canonical output and equal `qfb:v2` revisions. Present values, including empty
strings, are preserved; optional null handling never relaxes required fields.
Non-null string properties reject numbers, booleans and arrays instead of coercing
them into text. Explicit Textual coercion rules cover Jackson's string conversion
behavior in addition to the general scalar-coercion setting.

The public `qbank-v2.schema.json` is a **reader schema**: each optional property is
not required and accepts either null or its declared non-null type. Writer output
is the canonical subset of this schema with absent properties omitted. There is
no conflicting writer-only rule in the reader validation layer.

The audited optional properties are `Question.analysis`, `Question.evaluationSpec`,
`EvaluationSpec.evaluatorGuidance`, `InlineImageNode.alt`, `BlockImageNode.alt`,
`BlockImageNode.caption`, `SourceRef.documentTitle` and `SourceRef.sectionTitle`.
ESSAY adds optional `placeholder` and `referenceAnswer`.
The editor foundation adds optional paragraph/heading alignment, block-image
width/alignment, and inline-text marks. LINK has only required href/children;
every Resource metadata field is required. No optional properties exist on
stimuli or scores. The canonical writer omits absent properties, while the
reader treats explicit null as absent for these new fields as well.

Codec regression tests cover omission, explicit-null normalization, domain/hash
equality, valued/blank metadata round-trips, invalid optional types and required
null rejection. Validate the public schema separately (Python with `jsonschema`):

```powershell
python quizforge-infrastructure/src/test/python/check_qbank_v2_schema.py
```

## Revision identity

`QuestionBankV2Codec.contentId` serializes the validated logical model, excludes only
`assetId`, recursively sorts object keys and normalizes numeric scale before SHA-256.
The prefix is `qfb:v2:`. Array order is preserved, including question/option order.
Title, stimuli, rich node trees, payload, answer, score, evaluation, analysis,
recorded source snapshots, and resource metadata/hashes participate. Whitespace,
filesystem paths, mtime, ZIP entry layout, and UI state do not participate.

## Existing consumers and examples

`QuestionText` is the TEXT adapter for choice JavaFX views and persisted
choice practice snapshots. Unsupported choice RICH content or stimulus references get an explicit placeholder
and cannot enter the TEXT editor; it is never displayed by stringifying JSON.
Current rendering, submission and history behavior otherwise retain their existing
contracts. V4 database migration and existing archived rows are unchanged.

## ESSAY and prompt images

`EssayPayload(placeholder?)` and
`EssayAnswerSpec(referenceAnswer?: QuestionContent)` use the `ESSAY` discriminator.
Optional missing/null normalization uses the same canonical omission contract
as the other v2 fields. ESSAY has no word-limit controls, metadata, model fields,
validation or canonical schema properties. The reader removes only the obsolete
ESSAY payload properties `minWords` and `maxWords` before domain validation so
existing v2 packages remain readable; a normal save drops them. They do not
participate in normalized logical contentId. The schema describes normalized /
canonical JSON; raw obsolete properties are not schema-valid. User-authored
prompt text is preserved. This normalization does not introduce a v1 reader or
plain JSON package fallback; other unknown properties remain errors.

`RichContentEditor` is a generic authoring component; ESSAY prompt is its first
consumer. An image is a content capability, not a separate question type. The
component accepts/returns `QuestionContent` through an engine interface; it has
no ESSAY payload, type, label or control-ID dependency. Choice fields continue
to use their existing TEXT controls; referenceAnswer still uses a TEXT editor.

The first engine is locally bundled Tiptap/ProseMirror inside JavaFX WebView,
hosted by a separate `RichContentEditorWindow` Stage. The window is modal to its
owner and has a toolbar, surrounding workspace, a fixed-width 672 px content
canvas and Save/Cancel footer. Its content grows vertically without page breaks,
page numbers or A4 sizing; the outer window scrolls. The main ESSAY Edit page
shows a natural-height `QuestionContentRenderer` preview and an Edit button.
`RichContentEditor` and `RichContentEditorWindow` are generic infrastructure;
ESSAY prompt is the first consumer, and referenceAnswer remains a TEXT control.

The toolbar supports undo/redo, bold/italic/underline/strike/clear, paragraph,
H1–H3, left/center/right alignment, bullet/ordered lists, quote, link and image.
The adapter maps between Tiptap JSON and portable RichDocument nodes, including
the corresponding generic heading, list, quote, mark, link and image semantics.
Existing InlineImage nodes retain their model shape. Unsupported editor or
domain nodes are rejected, never silently dropped. The engine can be replaced
without changing QBank Domain. HTML is UI-only and never saved.
The checked-in local bundle is rebuilt from the pinned web dependencies with
`npm ci` and `npm run build` in `quizforge-desktop-app/rich-editor-web`; Desktop
runtime does not require npm, a dev server or network access.

`QuestionContentNormalizer` centralizes representation selection: TEXT remains
canonical lightweight representation when content is plain text only. Paragraphs
use blank-line separators and line breaks use newlines. Marks, headings, lists,
links, images, alignment and quotes require RICH; removing the final structured
feature can return TEXT. Opening and saving a plain TEXT prompt does not needlessly
convert it to RICH.

PNG/JPEG import checks signatures, decodes the image and bounds
bytes (32 MiB) and pixels (25 million). Each import uses a fresh `res_<uuid>`;
only resource IDs enter prompt nodes. Newly imported resources remain staged in a
`ContentEditSession` until Save; Cancel discards them and leaves the owning QBank
unchanged. The image toolbar supports insertion, replacement, deletion, relative
width 25/50/75/100% and left/center/right alignment. Formal package saves obtain
committed pending bytes and retained bytes from the existing package, with writer
SHA-256 and atomic replacement unchanged. Removing an image removes metadata only
when no question, option, reference answer, analysis or stimulus still uses it.

The generic `QuestionContentRenderer` maps `QuestionContent` to JavaFX nodes,
independent of ESSAY. Editor and Renderer share `QuestionContentLayout` values
for content width, font family/size, line height and paragraph spacing, plus
matching list and image width/alignment semantics. The Renderer grows to its
content height; the editor canvas keeps working space. This step uses it in
author Browse/Preview only; future Practice/History integration is deferred.
Missing, changed or undecodable package resources produce an explicit placeholder
while preserving the rest of the card.

The WebView loads only the application's bundled document and JS. CSP denies
network, frames, objects and external scripts; images use application-provided
data URLs inside the editor only. Navigation, popups and drops are blocked.
The minimal Java bridge only reports content height. Saving strictly converts
the editor JSON through the adapter into portable content, checks allowed fields
and resource identity, then uses Domain validation and the formal package writer.
Neither HTML nor image data URLs enter Domain/package JSON.

Banks containing ESSAY use an author preview with type, score and
prompt. They do not open a practice runtime or offer answer/history controls.
Existing choice-only Practice/History code and persistence remain at the Step 2
baseline, and DB schema/migrations are untouched. Previous subjective runtime
work is retained only in the safety stash, not in this foundation implementation.

English word count is centralized: ASCII letter/digit runs, allowing internal
apostrophes (straight/curly) and hyphens. Punctuation/whitespace separates words;
non-ASCII text alone does not count. The utility is retained without Practice integration.

`examples/step7-practice/Java集合练习.qbank` contains actual v2 single/multiple choice
packages. `examples/qbank-v2/rich-foundation.qbank` contains text + inline formula +
text + inline image, standalone block image/math, shared stimulus, IMAGE/AUDIO
metadata and an exact 1.5 default score. Its ZIP includes a tiny PNG and PCM WAV.

## Remaining boundaries

ESSAY Practice/Self Evaluation/History, stimulus UI, AI evaluation, Math/Audio editing, further question types and existing
user data migration remain separate tasks. Historical source-address support must not be
mistaken for permission to reintroduce a QBank v1 reader.

## Fixed acceptance workspace legacy asset (Step 2 checkpoint)

`C.qbank` in the fixed `Step 3 Live Acceptance` workspace has status
`LEGACY_UNSUPPORTED_ASSET` and does not block the Step 2 checkpoint. It belongs to
the abandoned legacy QBank format. Its source uses `sectionId = section_step3`;
there is no proven semantically equivalent mapping to an existing named anchor
in its Markdown document, so a lossless SourceRef migration is not possible.

The file remains unchanged, including its original SourceRef; no guessed anchor,
section-to-heading mapping or speculative migration is introduced. Its SHA-256
remains `9b79645f655a56a2b1ddf5fa098beee66e9c638c56fd4597b1c43200d70a47db`.
The current reader returns `INVALID_PACKAGE` (`ZIP end record missing`), and the
scanner isolates this as `INVALID_ASSET_FILE` while continuing to scan the
workspace and index supported neighboring assets.

QBank v2 provides no legacy reader, JSON fallback or runtime migration. This file
is not a QBank v2 runtime compatibility requirement. If needed in the future, the
acceptance asset may be recreated explicitly.

The other five specified files are migrated using the official package writer,
with source title snapshots retained from their legacy source metadata. The empty
`documents/test.qbank` remains an empty draft and is validated with
`validateEmptyDraft`; like populated packages, it has a `qfb:v2` registry revision.
