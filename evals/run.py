#!/usr/bin/env python3
"""ScenarioCraft judge evaluation runner.

The .yml files intentionally use the JSON-compatible subset of YAML so the eval gate has no
host dependency beyond Python 3. Live mode invokes the production judge without a shell;
recorded mode reads committed production-schema golden responses.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import sys
import tempfile
import unicodedata
from dataclasses import dataclass
from typing import Any


CRITERIA = ("theme_fit", "creativity", "effort", "detail")
CASE_ID = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
BLOCK_ID = re.compile(r"^[a-z0-9._-]+:[a-z0-9/._-]+$")
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


def has_unsafe_text(value: str) -> bool:
    return any(
        unicodedata.category(character) in ("Cc", "Cf", "Zl", "Zp")
        for character in value
    )


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
        return json.loads(
            _read_bytes(path, maximum),
            object_pairs_hook=unique_object,
            parse_constant=lambda value: (_ for _ in ()).throw(
                EvalError(f"non-finite number {value} in {path}")
            ),
        )
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise EvalError(f"invalid JSON-compatible YAML/JSON in {path}: {exc}") from exc


def load_task(path: Path) -> str:
    try:
        task = _read_bytes(path, MAX_TEXT_BYTES).decode("utf-8").strip()
    except UnicodeDecodeError as exc:
        raise EvalError(f"task must be UTF-8: {path}") from exc
    if not task or len(task) > 512 or has_unsafe_text(task):
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


def validate_repository_path(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value or len(value) > 512 or "\\" in value:
        raise EvalError(f"{label} must be a bounded repository-relative path")
    path = PurePosixPath(value)
    if path.is_absolute() or ".." in path.parts or str(path) != value:
        raise EvalError(f"{label} must be a bounded repository-relative path")
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
    if any(value < -(2**31) or value > 2**31 - 1 for value in voxel["origin"]):
        raise EvalError(f"{label} origin must use signed 32-bit integers")
    palette = voxel["palette"]
    if (
        not isinstance(palette, list)
        or not palette
        or palette[0] != "minecraft:air"
        or len(palette) > 4096
        or any(
            not isinstance(item, str)
            or len(item) > 256
            or not BLOCK_ID.fullmatch(item)
            for item in palette
        )
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
    source = expected["source"]
    if not isinstance(source, dict) or source.get("kind") not in ("synthetic", "family-round"):
        raise EvalError(f"{label} source kind must be synthetic or family-round")
    if source["kind"] == "synthetic":
        require_keys(
            source,
            {"kind", "reference", "response_origin"},
            f"{label} source",
        )
        if source["response_origin"] != "hand-authored-golden":
            raise EvalError(f"{label} synthetic response must be a hand-authored golden")
        if not isinstance(source["reference"], str) or not source["reference"].strip():
            raise EvalError(f"{label} source reference must be non-blank")
    else:
        require_keys(
            source,
            {
                "kind",
                "round_id",
                "plot_id",
                "artifact_commit",
                "voxel_artifact_path",
                "response_artifact_path",
                "voxel_sha256",
                "response_sha256",
                "response_origin",
            },
            f"{label} source",
        )
        if source["response_origin"] != "live-recording":
            raise EvalError(f"{label} family response must be a live recording")
        if not re.fullmatch(r"round-[0-9]{8}-[0-9]{6}", str(source["round_id"])):
            raise EvalError(f"{label} family round_id is invalid")
        if not re.fullmatch(r"p[1-9][0-9]*", str(source["plot_id"])):
            raise EvalError(f"{label} family plot_id is invalid")
        patterns = {
            "artifact_commit": r"[0-9a-f]{40}",
            "voxel_sha256": r"[0-9a-f]{64}",
            "response_sha256": r"[0-9a-f]{64}",
        }
        for field, pattern in patterns.items():
            if not re.fullmatch(pattern, str(source[field])):
                raise EvalError(f"{label} family {field} is invalid")
        validate_repository_path(
            source["voxel_artifact_path"], f"{label} family voxel_artifact_path"
        )
        validate_repository_path(
            source["response_artifact_path"], f"{label} family response_artifact_path"
        )
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


def file_sha256(path: Path) -> str:
    return hashlib.sha256(_read_bytes(path, MAX_JSON_BYTES)).hexdigest()


def validate_ground_truth(
    ground_truth_root: Path, specs_by_id: dict[str, CaseSpec]
) -> set[str]:
    if ground_truth_root.is_symlink() or not ground_truth_root.is_dir():
        raise EvalError(f"ground-truth directory is missing or symbolic: {ground_truth_root}")
    entries = sorted(ground_truth_root.iterdir())
    if any(
        path.is_symlink()
        or not path.is_file()
        or path.suffix != ".yml"
        or not CASE_ID.fullmatch(path.stem)
        for path in entries
    ):
        raise EvalError("ground-truth directory contains an unexpected entry")
    files = entries
    if not files:
        raise EvalError("ground-truth directory contains no review files")
    seen_cases: set[str] = set()
    for path in files:
        if path.is_symlink() or not CASE_ID.fullmatch(path.stem):
            raise EvalError(f"unsafe ground-truth file: {path}")
        root = require_keys(
            load_json(path, MAX_TEXT_BYTES),
            {
                "schema",
                "case_id",
                "round_id",
                "plot_id",
                "artifact_commit",
                "voxel_artifact_path",
                "response_artifact_path",
                "voxel_sha256",
                "response_sha256",
                "adult_supervised",
                "reviews",
            },
            path.name,
        )
        if root["schema"] != 1 or root["adult_supervised"] is not True:
            raise EvalError(f"{path.name} must use schema 1 and adult_supervised true")
        case_id = root["case_id"]
        if case_id not in specs_by_id or case_id != path.stem or case_id in seen_cases:
            raise EvalError(f"{path.name} must identify one unique existing eval case")
        spec = specs_by_id[case_id]
        source = spec.expected["source"]
        if source["kind"] != "family-round":
            raise EvalError(f"{path.name} may review only a family-round case")
        voxel_hash = file_sha256(spec.directory / "voxels.json")
        response_hash = file_sha256(spec.directory / "recorded-response.json")
        response = load_json(spec.directory / "recorded-response.json")
        contestants = response.get("contestants") if isinstance(response, dict) else None
        if (
            root["round_id"] != source["round_id"]
            or root["plot_id"] != source["plot_id"]
            or root["artifact_commit"] != source["artifact_commit"]
            or root["voxel_artifact_path"] != source["voxel_artifact_path"]
            or root["response_artifact_path"] != source["response_artifact_path"]
            or root["voxel_sha256"] != source["voxel_sha256"]
            or root["response_sha256"] != source["response_sha256"]
            or root["voxel_sha256"] != voxel_hash
            or root["response_sha256"] != response_hash
            or spec.voxel["plot_id"] != source["plot_id"]
            or not isinstance(contestants, list)
            or len(contestants) != 1
            or response.get("round_id") != source["round_id"]
            or not isinstance(contestants[0], dict)
            or contestants[0].get("plot_id") != source["plot_id"]
        ):
            raise EvalError(f"{path.name} is not bound to the exact reviewed artifacts")
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
                or has_unsafe_text(reason)
            ):
                raise EvalError(f"{path.name} reason must be safe one-line text")
        if ages != {7, 10}:
            raise EvalError(f"{path.name} must contain ages 7 and 10")
    return seen_cases


def verify_family_commits(specs: list[CaseSpec], repo_root: Path) -> None:
    for spec in specs:
        source = spec.expected["source"]
        if source["kind"] != "family-round":
            continue
        completed = subprocess.run(
            ["git", "cat-file", "-e", f"{source['artifact_commit']}^{{commit}}"],
            cwd=repo_root,
            capture_output=True,
            check=False,
        )
        if completed.returncode != 0:
            raise EvalError(
                f"{spec.case_id} artifact_commit does not resolve in this repository"
            )
        artifacts = (
            ("voxel", source["voxel_artifact_path"], source["voxel_sha256"]),
            ("response", source["response_artifact_path"], source["response_sha256"]),
        )
        for kind, artifact_path, expected_hash in artifacts:
            object_name = f"{source['artifact_commit']}:{artifact_path}"
            size = subprocess.run(
                ["git", "cat-file", "-s", object_name],
                cwd=repo_root,
                text=True,
                capture_output=True,
                check=False,
            )
            if size.returncode != 0 or not size.stdout.strip().isdigit():
                raise EvalError(
                    f"{spec.case_id} {kind} artifact is absent from artifact_commit"
                )
            if not 0 < int(size.stdout.strip()) <= MAX_JSON_BYTES:
                raise EvalError(
                    f"{spec.case_id} {kind} artifact has an invalid committed size"
                )
            content = subprocess.run(
                ["git", "show", object_name],
                cwd=repo_root,
                capture_output=True,
                check=False,
            )
            actual_hash = hashlib.sha256(content.stdout).hexdigest()
            if content.returncode != 0 or actual_hash != expected_hash:
                raise EvalError(
                    f"{spec.case_id} {kind} artifact does not match artifact_commit"
                )


def validate_results(
    value: Any,
    task: str,
    label: str,
    expected_round_id: str | None = None,
    expected_plot_id: str | None = None,
) -> list[dict[str, Any]]:
    results = require_keys(
        value,
        {"schema", "round_id", "task", "contestants", "winner"},
        label,
    )
    if results["schema"] != 1 or results["task"] != task:
        raise EvalError(f"{label} has the wrong schema or task")
    if not isinstance(results["round_id"], str) or not re.fullmatch(
        r"round-[0-9]{8}-[0-9]{6}", results["round_id"]
    ):
        raise EvalError(f"{label} round_id has an invalid format")
    if expected_round_id is not None and results["round_id"] != expected_round_id:
        raise EvalError(f"{label} round_id does not match family provenance")
    contestants = results["contestants"]
    if not isinstance(contestants, list) or len(contestants) != 1:
        raise EvalError(f"{label} must contain exactly one contestant")
    contestant = require_keys(
        contestants[0], {"plot_id", "player", "verdicts", "mean", "failures"}, f"{label} contestant"
    )
    required_plot_id = expected_plot_id or "p1"
    if contestant["plot_id"] != required_plot_id or not isinstance(contestant["player"], str):
        raise EvalError(f"{label} contestant identity is invalid")
    if contestant["failures"] != []:
        raise EvalError(f"{label} successful recorded response must not contain failures")
    verdicts = contestant["verdicts"]
    if not isinstance(verdicts, list) or len(verdicts) < 2:
        raise EvalError(f"{label} must contain at least two verdicts")
    verdict_means: list[float] = []
    for index, raw in enumerate(verdicts):
        verdict = require_keys(
            raw, {"persona", "reasoning", "scores", "comment"}, f"{label} verdict {index}"
        )
        if list(verdict).index("reasoning") > list(verdict).index("scores"):
            raise EvalError(f"{label} verdict {index} assigns scores before reasoning")
        for field in ("persona", "reasoning", "comment"):
            if not isinstance(verdict[field], str) or not verdict[field].strip():
                raise EvalError(f"{label} verdict {index} {field} must be non-blank")
            if has_unsafe_text(verdict[field]):
                raise EvalError(f"{label} verdict {index} {field} contains control text")
        if len(verdict["reasoning"]) > 4000 or len(verdict["comment"]) > 500:
            raise EvalError(f"{label} verdict {index} text exceeds the production limit")
        score = require_keys(verdict["scores"], set(CRITERIA), f"{label} verdict {index} scores")
        for criterion in CRITERIA:
            require_integer(score[criterion], f"{label} verdict {index} {criterion}", 1, 10)
        verdict_means.append(sum(score[criterion] for criterion in CRITERIA) / len(CRITERIA))
    computed_mean = sum(verdict_means) / len(verdict_means)
    recorded_mean = contestant["mean"]
    if (
        isinstance(recorded_mean, bool)
        or not isinstance(recorded_mean, (int, float))
        or not math.isfinite(recorded_mean)
        or abs(recorded_mean - computed_mean) > 1e-9
    ):
        raise EvalError(f"{label} contestant mean does not match its verdicts")
    winner = require_keys(
        results["winner"], {"plot_id", "player", "mean"}, f"{label} winner"
    )
    if (
        winner["plot_id"] != contestant["plot_id"]
        or winner["player"] != contestant["player"]
        or isinstance(winner["mean"], bool)
        or not isinstance(winner["mean"], (int, float))
        or not math.isfinite(winner["mean"])
        or abs(winner["mean"] - computed_mean) > 1e-9
    ):
        raise EvalError(f"{label} winner does not match the scored contestant")
    return verdicts


def evaluate_case(
    spec: CaseSpec,
    response: Any,
    expected_round_id: str | None = None,
    expected_plot_id: str | None = None,
) -> CaseResult:
    failures: list[str] = []
    try:
        verdicts = validate_results(
            response,
            spec.task,
            f"{spec.case_id} response",
            expected_round_id,
            expected_plot_id,
        )
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


def build_current_judge(repo_root: Path) -> None:
    completed = subprocess.run(
        [str(repo_root / "gradlew"), ":judge:installDist", "--no-daemon"],
        cwd=repo_root,
        text=True,
        capture_output=True,
        timeout=600,
        check=False,
    )
    if completed.returncode != 0:
        diagnostic = completed.stderr.strip().splitlines()[-1:] or ["unknown failure"]
        raise EvalError(f"could not build the current judge: {diagnostic[0]}")


def prepare_live_judge(repo_root: Path) -> None:
    if not os.environ.get("OPENAI_API_KEY", "").strip():
        raise EvalError("live mode requires OPENAI_API_KEY; use --dry-run in CI")
    build_current_judge(repo_root)


def validate_recorded_with_java(spec: CaseSpec, repo_root: Path) -> None:
    judge = repo_root / "judge" / "build" / "install" / "judge" / "bin" / "judge"
    environment = dict(os.environ)
    environment["SCENARIOCRAFT_JUDGE_CONFIG_DIR"] = str(repo_root / "judge")
    completed = subprocess.run(
        [
            str(judge),
            "--validate-recorded",
            str(spec.directory / "recorded-response.json"),
            "--task-file",
            str(spec.directory / "task.txt"),
        ],
        cwd=repo_root,
        env=environment,
        text=True,
        capture_output=True,
        timeout=60,
        check=False,
    )
    if completed.returncode != 0:
        diagnostic = completed.stderr.strip().splitlines()[-1:] or ["unknown failure"]
        raise EvalError(f"{spec.case_id} failed Java validation: {diagnostic[0]}")


def live_response(spec: CaseSpec, repo_root: Path) -> Any:
    judge = repo_root / "judge" / "build" / "install" / "judge" / "bin" / "judge"
    if not judge.is_file() or not os.access(judge, os.X_OK):
        raise EvalError("the freshly built live judge command is missing")
    with tempfile.TemporaryDirectory(prefix="scenariocraft-eval-") as temporary:
        temporary_root = Path(temporary)
        round_dir = temporary_root / "round-20260721-120000"
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
        config_directory = temporary_root / "config"
        config_directory.mkdir()
        shutil.copyfile(repo_root / "judge" / "personas.yml", config_directory / "personas.yml")
        shutil.copyfile(repo_root / "judge" / "rubric.md", config_directory / "rubric.md")
        environment = dict(os.environ)
        for name in list(environment):
            if name.startswith("SCENARIOCRAFT_RCON_"):
                environment.pop(name)
        environment["SCENARIOCRAFT_JUDGE_CONFIG_DIR"] = str(config_directory)
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
    production_validation: bool = True,
) -> int:
    specs = load_cases(cases_root)
    if len(specs) < 6:
        raise EvalError("eval suite must contain at least six cases")
    if ground_truth_root is not None:
        specs_by_id = {spec.case_id: spec for spec in specs}
        reviewed = validate_ground_truth(ground_truth_root, specs_by_id)
        family_cases = {
            spec.case_id
            for spec in specs
            if spec.expected["source"]["kind"] == "family-round"
        }
        if len(family_cases) < 2:
            raise EvalError("eval suite must contain at least two family-round cases")
        if not family_cases.issubset(reviewed):
            raise EvalError("every family-round case must have design-council ground truth")
        verify_family_commits(specs, repo_root)
    if not dry_run:
        prepare_live_judge(repo_root)
    elif production_validation:
        build_current_judge(repo_root)
    results: dict[str, CaseResult] = {}
    for spec in specs:
        response = (
            load_json(spec.directory / "recorded-response.json")
            if dry_run
            else live_response(spec, repo_root)
        )
        if dry_run and production_validation:
            validate_recorded_with_java(spec, repo_root)
        source = spec.expected["source"]
        family_recording = dry_run and source["kind"] == "family-round"
        results[spec.case_id] = evaluate_case(
            spec,
            response,
            source["round_id"] if family_recording else None,
            source["plot_id"] if family_recording else None,
        )
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
