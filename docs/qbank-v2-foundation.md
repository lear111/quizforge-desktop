# QBank v2 Step 1: logical format foundation

The current `.qbank` is ordinary JSON. Only `schemaVersion: "2.0"` is accepted.
There is no v1 reader, DTO, field fallback, or production migration. ZIP and media IO
are deliberately outside Step 1. Existing user workspaces are never rewritten.

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
    ├── QuestionPayload → ChoicePayload(options: ChoiceOption[])
    ├── QuestionAnswerSpec → ChoiceAnswerSpec(correctOptionIds)
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
and image resource references, and matching resource kinds. Resource metadata need
not resolve to real bytes in Step 1. Locators are relative logical paths: no drive
letters, absolute paths, traversal segments, backslashes, or data/base64 URIs.

Only SINGLE_CHOICE and MULTIPLE_CHOICE exist. Single choice requires exactly one
correct option. Multiple choice retains the current rule: at least two correct
options, with at least one incorrect option. Option IDs remain unique bank-wide.
Questions may have no source references. Analysis and evaluation are optional.
TEXT prompts/options cannot be blank; TEXT analysis may be blank or absent.

`ScoreSpec.defaultMaxScore` uses `BigDecimal`, is required and positive. Newly
created questions default to `BigDecimal.ONE`. Editor mutations preserve score,
evaluation, stimuli and resource metadata; no new score controls are added.

Evaluation criterion weights use `BigDecimal`, are positive, and sum exactly to 1
when criteria are nonempty. This gives portable normalized relative weights without
an implicit normalization algorithm or floating point tolerance. Empty criteria
are valid; no AI or evaluation runtime is introduced.

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
LINK has only required href/children; every Resource metadata field is required.
No optional properties exist on the other current rich nodes, stimuli or scores.

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

`QuestionText` is the single TEXT adapter for current JavaFX views and persisted
text practice snapshots. Unsupported RICH content or stimulus references get an explicit placeholder
and cannot enter the TEXT editor; it is never displayed by stringifying JSON.
Current rendering, submission and history behavior otherwise retain their existing
contracts. V4 database migration and archived rows are unchanged; no actual score
award, rich snapshot renderer, stimulus UI or media IO is added.

`examples/step7-practice/Java集合练习.qbank` contains actual v2 single/multiple choice
JSON. `examples/qbank-v2/rich-foundation.qbank` contains text + inline formula +
text + inline image, standalone block image/math, shared stimulus, IMAGE/AUDIO
metadata and an exact 1.5 default score. It intentionally has no resource binaries.

## Before ZIP Step 2

Step 2 must add package IO and validate actual resource bytes against metadata
and hashes. It must decide package entry canonicalization and archive safety.
Rich/stimulus UI, non-choice types, scoring runtime, and existing user data
migration remain separate tasks. Historical source-address support must not be
mistaken for permission to reintroduce a QBank v1 reader.
