#!/usr/bin/env python3
"""ScenarioCraft judge evaluation runner.

The .yml files intentionally use the JSON-compatible subset of YAML so the eval gate has no
host dependency beyond Python 3. Live mode invokes the installed production judge without a
shell; recorded mode reads committed production results.json responses.
"""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from typing import Any


CRITERIA = ("theme_fit", "creativity", "effort", "detail")
CASE_ID = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
POSITIVE = re.compile(
    r"\b(?:beautiful|bold|bright|charming|clear|clever|colorful|colourful|cozy|"
    r"creative|delightful|detailed|excellent|fantastic|good|great|impressive|"
    r"inviting|lovely|neat|recognizable|solid|strong|sturdy|tidy|warm|welcoming|"
    r"works?)\b",
    re.IGNORECASE,
)
FEATURE = re.compile(
    r"\b(?:arch|block|bridge|build|chimney|color|colour|detail|design|door|"
    r"floor|garden|idea|interior|lighting|outline|palette|path|pattern|roof|room|"
    r"shape|silhouette|structure|texture|tower|trim|wall|window)s?\b",
    re.IGNORECASE,
)
MAX_JSON_BYTES = 16 * 1024 * 1024
MAX_TEXT_BYTES = 64 * 1024


class EvalError(Exception):
    """A user-actionable eval contract failure."""


@dataclass(frozen=True)
class CaseSpec:
    case_id: str
    directory: Path
    task: str
    voxel: dict[str, Any]
    expected: dict[str, Any]


@dataclass
class CaseResult:
    case_id: str
    mean: float | None
    failures: list[str]


def _read_bytes(path: Path, maximum: int) -> bytes:
    if path.is_symlink() or not path.is_file():
        raise EvalError(f"required regular file is missing or symbolic: {path}")
    size = path.stat().st_size
    if size <= 0 or size > maximum:
        raise EvalError(f"file size is outside the allowed range: {path}")
    return path.read_bytes()


def load_json(path: Path, maximum: int = MAX_JSON_BYTES) -> Any:
    def unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
        value: dict[str, Any] = {}
        for key, item in pairs:
            if key in value:
                raise EvalError(f"duplicate key {key!r} in {path}")
            value[key] = item
        return value

    try:
        return json.loads(_read_bytes(path, maximum), object_pairs_hook=unique_object)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise EvalError(f"invalid JSON-compatible YAML/JSON in {path}: {exc}") from exc


def load_task(path: Path) -> str:
    try:
        task = _read_bytes(path, MAX_TEXT_BYTES).decode("utf-8").strip()
    except UnicodeDecodeError as exc:
        raise EvalError(f"task must be UTF-8: {path}") from exc
    if not task or len(task) > 512 or any(ord(char) < 32 for char in task):
        raise EvalError(f"task must be safe, non-blank text of at most 512 characters: {path}")
    return task


def require_keys(value: Any, expected: set[str], label: str) -> dict[str, Any]:
    if not isinstance(value, dict) or set(value) != expected:
        raise EvalError(f"{label} must contain exactly these keys: {sorted(expected)}")
    return value


def require_integer(value: Any, label: str, minimum: int, maximum: int) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or not minimum <= value <= maximum:
        raise EvalError(f"{label} must be an integer from {minimum} through {maximum}")
    return value


def validate_voxel(value: Any, label: str) -> dict[str, Any]:
    voxel = require_keys(
        value, {"schema", "plot_id", "origin", "size", "palette", "blocks"}, label
    )
    if voxel["schema"] != 1:
        raise EvalError(f"{label} schema must be 1")
    if not isinstance(voxel["plot_id"], str) or not voxel["plot_id"].strip():
        raise EvalError(f"{label} plot_id must be non-blank")
    for field in ("origin", "size"):
        if (
            not isinstance(voxel[field], list)
            or len(voxel[field]) != 3
            or any(isinstance(item, bool) or not isinstance(item, int) for item in voxel[field])
        ):
            raise EvalError(f"{label} {field} must contain three integers")
    if any(size < 0 or size > 256 for size in voxel["size"]):
        raise EvalError(f"{label} size is outside the supported range")
    palette = voxel["palette"]
    if (
        not isinstance(palette, list)
        or not palette
        or palette[0] != "minecraft:air"
        or len(palette) > 4096
        or any(not isinstance(item, str) or not item or len(item) > 256 for item in palette)
    ):
        raise EvalError(f"{label} palette is invalid")
    blocks = voxel["blocks"]
    volume = voxel["size"][0] * voxel["size"][1] * voxel["size"][2]
    if not isinstance(blocks, list) or len(blocks) != volume or volume > 1_000_000:
        raise EvalError(f"{label} blocks length must equal the bounded size volume")
    if any(
        isinstance(item, bool)
        or not isinstance(item, int)
        or item < 0
        or item >= len(palette)
        for item in blocks
    ):
        raise EvalError(f"{label} blocks contain an invalid palette index")
    return voxel


def validate_expected(value: Any, label: str) -> dict[str, Any]:
    expected = require_keys(
        value,
        {"schema", "source", "scores", "ordering", "tone", "structure"},
        label,
    )
    if expected["schema"] != 1:
        raise EvalError(f"{label} schema must be 1")
    source = require_keys(expected["source"], {"kind", "reference"}, f"{label} source")
    if source["kind"] not in ("synthetic", "family-round"):
        raise EvalError(f"{label} source kind must be synthetic or family-round")
    if not isinstance(source["reference"], str) or not source["reference"].strip():
        raise EvalError(f"{label} source reference must be non-blank")
    scores = require_keys(expected["scores"], set(CRITERIA), f"{label} scores")
    for criterion in CRITERIA:
        band = require_keys(scores[criterion], {"min", "max"}, f"{label} {criterion}")
        low = require_integer(band["min"], f"{label} {criterion} min", 1, 10)
        high = require_integer(band["max"], f"{label} {criterion} max", 1, 10)
        if low > high:
            raise EvalError(f"{label} {criterion} min must not exceed max")
    ordering = require_keys(
        expected["ordering"], {"higher_than", "lower_than"}, f"{label} ordering"
    )
    for relation in ("higher_than", "lower_than"):
        targets = ordering[relation]
        if (
            not isinstance(targets, list)
            or any(not isinstance(item, str) or not CASE_ID.fullmatch(item) for item in targets)
        ):
            raise EvalError(f"{label} ordering {relation} must contain safe case IDs")
    tone = require_keys(
        expected["tone"], {"genuine_positive", "banned_phrases"}, f"{label} tone"
    )
    if tone["genuine_positive"] is not True:
        raise EvalError(f"{label} must require a genuine positive")
    if (
        not isinstance(tone["banned_phrases"], list)
        or any(not isinstance(item, str) or not item.strip() for item in tone["banned_phrases"])
    ):
        raise EvalError(f"{label} banned_phrases must contain non-blank strings")
    structure = require_keys(
        expected["structure"], {"valid_schema", "reason_precedes_score"}, f"{label} structure"
    )
    if structure != {"valid_schema": True, "reason_precedes_score": True}:
        raise EvalError(f"{label} must require valid schema and reason-before-score")
    return expected


def load_cases(cases_root: Path) -> list[CaseSpec]:
    if cases_root.is_symlink() or not cases_root.is_dir():
        raise EvalError(f"cases directory is missing or symbolic: {cases_root}")
    directories = sorted(path for path in cases_root.iterdir() if path.is_dir())
    if not directories:
        raise EvalError("eval suite contains no cases")
    if len(directories) > 100:
        raise EvalError("eval suite exceeds the 100-case limit")
    specs: list[CaseSpec] = []
    for directory in directories:
        case_id = directory.name
        if directory.is_symlink() or not CASE_ID.fullmatch(case_id):
            raise EvalError(f"unsafe eval case directory: {directory}")
        specs.append(
            CaseSpec(
                case_id,
                directory,
                load_task(directory / "task.txt"),
                validate_voxel(load_json(directory / "voxels.json"), f"{case_id} voxels"),
                validate_expected(
                    load_json(directory / "expected.yml", MAX_TEXT_BYTES),
                    f"{case_id} expected.yml",
                ),
            )
        )
    return specs


def validate_ground_truth(ground_truth_root: Path, case_ids: set[str]) -> set[str]:
    if ground_truth_root.is_symlink() or not ground_truth_root.is_dir():
        raise EvalError(f"ground-truth directory is missing or symbolic: {ground_truth_root}")
    files = sorted(ground_truth_root.glob("*.yml"))
    if not files:
        raise EvalError("ground-truth directory contains no review files")
    seen_cases: set[str] = set()
    for path in files:
        if path.is_symlink() or not CASE_ID.fullmatch(path.stem):
            raise EvalError(f"unsafe ground-truth file: {path}")
        root = require_keys(
            load_json(path, MAX_TEXT_BYTES),
            {"schema", "case_id", "adult_supervised", "reviews"},
            path.name,
        )
        if root["schema"] != 1 or root["adult_supervised"] is not True:
            raise EvalError(f"{path.name} must use schema 1 and adult_supervised true")
        case_id = root["case_id"]
        if case_id not in case_ids or case_id != path.stem or case_id in seen_cases:
            raise EvalError(f"{path.name} must identify one unique existing eval case")
        seen_cases.add(case_id)
        reviews = root["reviews"]
        if not isinstance(reviews, list) or len(reviews) != 2:
            raise EvalError(f"{path.name} must contain exactly the age-7 and age-10 reviews")
        ages: set[int] = set()
        for index, raw_review in enumerate(reviews):
            review = require_keys(
                raw_review,
                {"age", "role", "agreement", "reason"},
                f"{path.name} review {index}",
            )
            age = review["age"]
            if age not in (7, 10) or isinstance(age, bool) or age in ages:
                raise EvalError(f"{path.name} reviews must contain unique ages 7 and 10")
            ages.add(age)
            if review["role"] != "judge auditor":
                raise EvalError(f"{path.name} review role must be judge auditor")
            if review["agreement"] not in ("agree", "disagree"):
                raise EvalError(f"{path.name} agreement must be agree or disagree")
            reason = review["reason"]
            if (
                not isinstance(reason, str)
                or not reason.strip()
                or len(reason) > 240
                or any(character in reason for character in "\r\n")
                or any(ord(character) < 32 for character in reason)
            ):
                raise EvalError(f"{path.name} reason must be safe one-line text")
        if ages != {7, 10}:
            raise EvalError(f"{path.name} must contain ages 7 and 10")
    return seen_cases


def validate_results(value: Any, task: str, label: str) -> list[dict[str, Any]]:
    results = require_keys(
        value,
        {"schema", "round_id", "task", "contestants", "winner"},
        label,
    )
    if results["schema"] != 1 or results["task"] != task:
        raise EvalError(f"{label} has the wrong schema or task")
    contestants = results["contestants"]
    if not isinstance(contestants, list) or len(contestants) != 1:
        raise EvalError(f"{label} must contain exactly one contestant")
    contestant = require_keys(
        contestants[0], {"plot_id", "player", "verdicts", "mean", "failures"}, f"{label} contestant"
    )
    verdicts = contestant["verdicts"]
    if not isinstance(verdicts, list) or len(verdicts) < 2:
        raise EvalError(f"{label} must contain at least two verdicts")
    for index, raw in enumerate(verdicts):
        verdict = require_keys(
            raw, {"persona", "reasoning", "scores", "comment"}, f"{label} verdict {index}"
        )
        if list(verdict).index("reasoning") > list(verdict).index("scores"):
            raise EvalError(f"{label} verdict {index} assigns scores before reasoning")
        for field in ("persona", "reasoning", "comment"):
            if not isinstance(verdict[field], str) or not verdict[field].strip():
                raise EvalError(f"{label} verdict {index} {field} must be non-blank")
        score = require_keys(verdict["scores"], set(CRITERIA), f"{label} verdict {index} scores")
        for criterion in CRITERIA:
            require_integer(score[criterion], f"{label} verdict {index} {criterion}", 1, 10)
    return verdicts


def evaluate_case(spec: CaseSpec, response: Any) -> CaseResult:
    failures: list[str] = []
    try:
        verdicts = validate_results(response, spec.task, f"{spec.case_id} response")
    except EvalError as exc:
        return CaseResult(spec.case_id, None, [str(exc)])
    criterion_means: dict[str, float] = {}
    for criterion in CRITERIA:
        mean = sum(item["scores"][criterion] for item in verdicts) / len(verdicts)
        criterion_means[criterion] = mean
        band = spec.expected["scores"][criterion]
        if not band["min"] <= mean <= band["max"]:
            failures.append(
                f"{criterion} mean {mean:.2f} is outside {band['min']}..{band['max']}"
            )
    banned = [item.casefold() for item in spec.expected["tone"]["banned_phrases"]]
    for index, verdict in enumerate(verdicts, start=1):
        combined = f"{verdict['reasoning']} {verdict['comment']}".casefold()
        found = next((phrase for phrase in banned if phrase in combined), None)
        if found:
            failures.append(f"verdict {index} contains banned phrase: {found}")
        first_sentence = re.split(r"[.!?]", verdict["comment"], maxsplit=1)[0]
        if not FEATURE.search(first_sentence) or not POSITIVE.search(first_sentence):
            failures.append(f"verdict {index} does not name a genuine concrete positive first")
    overall = sum(criterion_means.values()) / len(CRITERIA)
    return CaseResult(spec.case_id, overall, failures)


def evaluate_ordering(specs: list[CaseSpec], results: dict[str, CaseResult]) -> None:
    for spec in specs:
        current = results[spec.case_id]
        if current.mean is None:
            continue
        ordering = spec.expected["ordering"]
        for target_id in ordering["higher_than"]:
            target = results.get(target_id)
            if target is None:
                current.failures.append(f"ordering references missing case {target_id}")
            elif target.mean is not None and current.mean <= target.mean:
                current.failures.append(
                    f"mean {current.mean:.2f} must be higher than {target_id} {target.mean:.2f}"
                )
        for target_id in ordering["lower_than"]:
            target = results.get(target_id)
            if target is None:
                current.failures.append(f"ordering references missing case {target_id}")
            elif target.mean is not None and current.mean >= target.mean:
                current.failures.append(
                    f"mean {current.mean:.2f} must be lower than {target_id} {target.mean:.2f}"
                )


def live_response(spec: CaseSpec, repo_root: Path) -> Any:
    judge = repo_root / "judge" / "build" / "install" / "judge" / "bin" / "judge"
    if not judge.is_file() or not os.access(judge, os.X_OK):
        raise EvalError("live mode requires ./gradlew :judge:installDist first")
    if not os.environ.get("OPENAI_API_KEY", "").strip():
        raise EvalError("live mode requires OPENAI_API_KEY; use --dry-run in CI")
    with tempfile.TemporaryDirectory(prefix="scenariocraft-eval-") as temporary:
        round_dir = Path(temporary) / "round-20260721-120000"
        round_dir.mkdir()
        voxel = dict(spec.voxel)
        voxel["plot_id"] = "p1"
        (round_dir / "p1.voxels.json").write_text(json.dumps(voxel), encoding="utf-8")
        manifest = {
            "schema": 1,
            "round_id": round_dir.name,
            "task": spec.task,
            "world": "eval_world",
            "plots": [
                {
                    "plot_id": "p1",
                    "player": "Eval builder",
                    "origin": voxel["origin"],
                    "size": voxel["size"],
                }
            ],
        }
        (round_dir / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
        environment = dict(os.environ)
        environment["SCENARIOCRAFT_JUDGE_CONFIG_DIR"] = str(repo_root / "judge")
        completed = subprocess.run(
            [str(judge), "--round", str(round_dir)],
            cwd=repo_root,
            env=environment,
            text=True,
            capture_output=True,
            timeout=900,
            check=False,
        )
        if completed.returncode != 0:
            diagnostic = completed.stderr.strip().splitlines()[-1:] or ["unknown failure"]
            raise EvalError(f"live judge failed for {spec.case_id}: {diagnostic[0]}")
        return load_json(round_dir / "results.json")


def run(
    cases_root: Path,
    repo_root: Path,
    dry_run: bool,
    ground_truth_root: Path | None = None,
) -> int:
    specs = load_cases(cases_root)
    if len(specs) < 6:
        raise EvalError("eval suite must contain at least six cases")
    if ground_truth_root is not None:
        reviewed = validate_ground_truth(
            ground_truth_root, {spec.case_id for spec in specs}
        )
        family_cases = {
            spec.case_id
            for spec in specs
            if spec.expected["source"]["kind"] == "family-round"
        }
        if len(family_cases) < 2:
            raise EvalError("eval suite must contain at least two family-round cases")
        if not family_cases.issubset(reviewed):
            raise EvalError("every family-round case must have design-council ground truth")
    results: dict[str, CaseResult] = {}
    for spec in specs:
        response = (
            load_json(spec.directory / "recorded-response.json")
            if dry_run
            else live_response(spec, repo_root)
        )
        results[spec.case_id] = evaluate_case(spec, response)
    evaluate_ordering(specs, results)
    print(f"{'CASE':<32} {'RESULT':<6} DETAILS")
    print(f"{'-' * 32} {'-' * 6} {'-' * 30}")
    for spec in specs:
        result = results[spec.case_id]
        status = "PASS" if not result.failures else "FAIL"
        detail = f"mean={result.mean:.2f}" if result.mean is not None else "no score"
        if result.failures:
            detail = "; ".join(result.failures)
        print(f"{result.case_id:<32} {status:<6} {detail}")
    failed = sum(bool(result.failures) for result in results.values())
    print(f"\n{len(results) - failed}/{len(results)} cases passed")
    return 1 if failed else 0


def main(arguments: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Run ScenarioCraft judge evals")
    parser.add_argument("--dry-run", action="store_true", help="use recorded responses")
    parser.add_argument("--cases", type=Path, help=argparse.SUPPRESS)
    options = parser.parse_args(arguments)
    repo_root = Path(__file__).resolve().parent.parent
    cases_root = options.cases or repo_root / "evals" / "cases"
    ground_truth_root = repo_root / "evals" / "ground-truth"
    try:
        return run(
            cases_root.resolve(),
            repo_root,
            options.dry_run,
            ground_truth_root if ground_truth_root.exists() else None,
        )
    except (EvalError, OSError, subprocess.SubprocessError) as exc:
        print(f"Eval runner failed: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
