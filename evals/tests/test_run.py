import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest


RUNNER = Path(__file__).resolve().parents[1] / "run.py"
SPEC = importlib.util.spec_from_file_location("scenario_evals", RUNNER)
assert SPEC and SPEC.loader
scenario_evals = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = scenario_evals
SPEC.loader.exec_module(scenario_evals)


def expected(higher_than=None):
    return {
        "schema": 1,
        "source": {"kind": "synthetic", "reference": "unit test"},
        "scores": {
            criterion: {"min": 1, "max": 10} for criterion in scenario_evals.CRITERIA
        },
        "ordering": {"higher_than": higher_than or [], "lower_than": []},
        "tone": {"genuine_positive": True, "banned_phrases": ["you failed"]},
        "structure": {"valid_schema": True, "reason_precedes_score": True},
    }


def response(task, score):
    verdicts = []
    for persona in ("One", "Two"):
        verdicts.append(
            {
                "persona": persona,
                "reasoning": "The visible blocks form a deliberate structure.",
                "scores": {criterion: score for criterion in scenario_evals.CRITERIA},
                "comment": "Your block design has a clear shape. Try adding a bright window next.",
            }
        )
    return {
        "schema": 1,
        "round_id": "round-20260721-120000",
        "task": task,
        "contestants": [
            {
                "plot_id": "p1",
                "player": "Eval builder",
                "verdicts": verdicts,
                "mean": float(score),
                "failures": [],
            }
        ],
        "winner": {"plot_id": "p1", "player": "Eval builder", "mean": float(score)},
    }


def write_case(root, case_id, score, higher_than=None):
    directory = root / case_id
    directory.mkdir()
    task = "Build a tiny tower"
    (directory / "task.txt").write_text(task, encoding="utf-8")
    (directory / "voxels.json").write_text(
        json.dumps(
            {
                "schema": 1,
                "plot_id": case_id,
                "origin": [0, 64, 0],
                "size": [1, 1, 1],
                "palette": ["minecraft:air", "minecraft:stone"],
                "blocks": [1],
            }
        ),
        encoding="utf-8",
    )
    (directory / "expected.yml").write_text(
        json.dumps(expected(higher_than)), encoding="utf-8"
    )
    (directory / "recorded-response.json").write_text(
        json.dumps(response(task, score)), encoding="utf-8"
    )


class EvalRunnerTest(unittest.TestCase):
    def test_recorded_suite_passes_score_tone_structure_and_ordering(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            write_case(root, "empty", 2)
            write_case(root, "detailed", 8, ["empty"])
            self.assertEqual(0, scenario_evals.run(root, RUNNER.parents[1], True))

    def test_banned_phrase_and_reversed_order_fail(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            write_case(root, "empty", 8)
            write_case(root, "detailed", 2, ["empty"])
            recorded = root / "detailed" / "recorded-response.json"
            value = json.loads(recorded.read_text(encoding="utf-8"))
            value["contestants"][0]["verdicts"][0]["comment"] = (
                "Your block design has a clear shape. You failed to add a window."
            )
            recorded.write_text(json.dumps(value), encoding="utf-8")
            self.assertEqual(1, scenario_evals.run(root, RUNNER.parents[1], True))

    def test_scores_before_reasoning_are_rejected(self):
        verdict = {
            "persona": "One",
            "scores": {criterion: 5 for criterion in scenario_evals.CRITERIA},
            "reasoning": "Visible structure supports the scores.",
            "comment": "Your block design has a clear shape. Try adding a bright window next.",
        }
        value = response("Build a tiny tower", 5)
        value["contestants"][0]["verdicts"][0] = verdict
        with self.assertRaisesRegex(scenario_evals.EvalError, "before reasoning"):
            scenario_evals.validate_results(value, "Build a tiny tower", "case")

    def test_voxel_volume_mismatch_is_rejected(self):
        value = {
            "schema": 1,
            "plot_id": "p1",
            "origin": [0, 64, 0],
            "size": [2, 1, 1],
            "palette": ["minecraft:air"],
            "blocks": [0],
        }
        with self.assertRaisesRegex(scenario_evals.EvalError, "size volume"):
            scenario_evals.validate_voxel(value, "case")


if __name__ == "__main__":
    unittest.main()
