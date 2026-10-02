"""Validate the public reader schema with jsonschema (Draft 2020-12)."""
from copy import deepcopy
from pathlib import Path
import json
from zipfile import ZipFile

from jsonschema import Draft202012Validator


root = Path(__file__).resolve().parents[4]
schema = json.loads((root / "quizforge-infrastructure/src/main/resources/schema/qbank-v2.schema.json").read_text(encoding="utf-8"))
Draft202012Validator.check_schema(schema)
validator = Draft202012Validator(schema)

def logical_bank(relative):
    with ZipFile(root / relative) as package:
        manifest = json.loads(package.read("manifest.json"))
        body = json.loads(package.read("bank.json"))
    return {**{key: manifest[key] for key in ("assetId", "title", "schemaVersion")},
            "resources": [{("locator" if key == "path" else key): value for key, value in resource.items()}
                          for resource in manifest["resources"]], **body}


packages = ("examples/step7-practice/Java集合练习.qbank", "examples/qbank-v2/rich-foundation.qbank",
            "examples/qbank-v2/cloze-first-version.qbank", "examples/qbank-v2/reading-first-version.qbank",
            "examples/qbank-v2/matching-first-version.qbank", "examples/qbank-v2/translation-first-version.qbank")
for relative in packages:
    validator.validate(logical_bank(relative))

fixture = logical_bank("examples/qbank-v2/rich-foundation.qbank")
question = fixture["questions"][0]
question["evaluationSpec"] = {"criteria": [], "evaluatorGuidance": "Guidance"}
question["sourceRefs"] = [{
    "documentAssetId": "doc_schema",
    "documentContentId": "qfd:v2:" + "a" * 64,
    "anchorName": "Source", "occurrence": 1,
    "documentTitle": "Document", "sectionTitle": "Section",
}]


def parent_at(tree, path):
    node = tree
    for key in path[:-1]:
        node = node[key]
    return node, path[-1]


q = ("questions", 0)
optional = (
    (q + ("analysis",), {"kind": "TEXT", "text": "Analysis"}),
    (q + ("evaluationSpec",), {"criteria": []}),
    (q + ("evaluationSpec", "evaluatorGuidance"), "Guidance"),
    (q + ("prompt", "document", "blocks", 0, "children", 3, "alt"), "Inline image"),
    (q + ("prompt", "document", "blocks", 0, "alignment"), "CENTER"),
    (q + ("prompt", "document", "blocks", 0, "children", 0, "marks"), ["BOLD"]),
    (q + ("prompt", "document", "blocks", 1, "alt"), "Block image"),
    (q + ("prompt", "document", "blocks", 1, "caption"), "Caption"),
    (q + ("prompt", "document", "blocks", 1, "widthPercent"), 50),
    (q + ("prompt", "document", "blocks", 1, "alignment"), "RIGHT"),
    (q + ("sourceRefs", 0, "documentTitle"), "Document"),
    (q + ("sourceRefs", 0, "sectionTitle"), "Section"),
)
cases = 0
for path, value in optional:
    for mode in ("missing", "null", "value"):
        candidate = deepcopy(fixture)
        parent, key = parent_at(candidate, path)
        if mode == "missing":
            parent.pop(key, None)
        else:
            parent[key] = None if mode == "null" else value
        validator.validate(candidate)
        cases += 1
    for wrong_value in (([1], 42, 1.25, True) if path[-1] == "marks" else ([], 42, 1.25, True)):
        invalid = deepcopy(fixture)
        parent, key = parent_at(invalid, path)
        parent[key] = wrong_value
        assert not validator.is_valid(invalid), f"Non-null optional type accepted: {path}"
        cases += 1

# Required properties must not gain the optional-null relaxation.
for path in (q + ("prompt",), q + ("scoreSpec",), q + ("stimulusRefs",),
             q + ("sourceRefs", 0, "anchorName"), ("resources", 0, "sha256")):
    invalid = deepcopy(fixture)
    parent, key = parent_at(invalid, path)
    parent[key] = None
    assert not validator.is_valid(invalid), f"Required null accepted: {path}"
    cases += 1

expected_optional = {
    ("question", "analysis"), ("question", "evaluationSpec"),
    ("evaluation", "evaluatorGuidance"), ("inlineImage", "alt"),
    ("blockImage", "alt"), ("blockImage", "caption"),
    ("source", "documentTitle"), ("source", "sectionTitle"),
    ("essayPayload", "placeholder"), ("essayAnswer", "referenceAnswer"),
    ("paragraph", "alignment"), ("heading", "alignment"),
    ("inlineText", "marks"), ("blockImage", "widthPercent"), ("blockImage", "alignment"),
}
actual_optional = {
    (name, field)
    for name, definition in schema["$defs"].items()
    for field in definition.get("properties", {})
    if field not in definition.get("required", [])
}
assert actual_optional == expected_optional, "Audit optional fields when the model changes"
essay = deepcopy(fixture)
essay_question = essay["questions"][0]
essay_question.update(type="ESSAY", stimulusRefs=[], payload={"kind": "ESSAY"}, answerSpec={"kind": "ESSAY"})
validator.validate(essay)
for field, value in (("placeholder", "Write here"),
                     ("referenceAnswer", {"kind": "TEXT", "text": "Sample"})):
    owner = "answerSpec" if field == "referenceAnswer" else "payload"
    for tested in (None, value):
        candidate = deepcopy(essay)
        candidate["questions"][0][owner][field] = tested
        validator.validate(candidate)
        cases += 1
    for wrong in ([], True, 1.5):
        invalid = deepcopy(essay)
        invalid["questions"][0][owner][field] = wrong
        assert not validator.is_valid(invalid), f"Invalid essay optional accepted: {field}"
        cases += 1
for field, wrong in (("minWords", 160), ("maxWords", 200)):
    invalid = deepcopy(essay)
    invalid["questions"][0]["payload"][field] = wrong
    assert not validator.is_valid(invalid)
    cases += 1
for wrong_type in ("SINGLE_CHOICE", "MULTIPLE_CHOICE"):
    invalid = deepcopy(essay)
    invalid["questions"][0]["type"] = wrong_type
    assert not validator.is_valid(invalid), "Question type and payload must agree"
    cases += 1
for relative, expected_type in zip(packages[2:], ("CLOZE", "READING", "MATCHING", "TRANSLATION")):
    candidate = logical_bank(relative)
    index = next(i for i, item in enumerate(candidate["questions"]) if item["type"] == expected_type)
    candidate["questions"][index]["type"] = "ESSAY"
    assert not validator.is_valid(candidate), f"Mismatched {expected_type} payload accepted as ESSAY"
    cases += 1
print(f"PASS: Draft 2020-12 schema, {len(packages)} packages and essay fixture, {cases} contract cases, all {len(expected_optional)} optional fields")
