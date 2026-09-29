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


for relative in ("examples/step7-practice/Java集合练习.qbank", "examples/qbank-v2/rich-foundation.qbank"):
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
    (q + ("prompt", "document", "blocks", 1, "alt"), "Block image"),
    (q + ("prompt", "document", "blocks", 1, "caption"), "Caption"),
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
    for wrong_value in ([], 42, 1.25, True):
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
}
actual_optional = {
    (name, field)
    for name, definition in schema["$defs"].items()
    for field in definition.get("properties", {})
    if field not in definition.get("required", [])
}
assert actual_optional == expected_optional, "Audit optional fields when the model changes"
print(f"PASS: Draft 2020-12 schema, 2 examples, {cases} optional/required-null cases, all 8 optional fields")
