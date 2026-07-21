from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parent.parent


class FamilyServerContractTest(unittest.TestCase):
    def test_family_configuration_is_ten_minutes_with_full_deck(self):
        config = (ROOT / "demo" / "family-config.yml").read_text(encoding="utf-8")

        self.assertIn("build-seconds: 600", config)
        task_block = config.split("tasks:\n", 1)[1].split("\n\n", 1)[0]
        self.assertGreaterEqual(len(re.findall(r"^  - ", task_block, re.MULTILINE)), 30)

    def test_compose_defaults_to_family_config_and_exposes_bedrock(self):
        compose = (ROOT / "docker-compose.yml").read_text(encoding="utf-8")
        bedrock = (ROOT / "docker-compose.bedrock.yml").read_text(encoding="utf-8")

        self.assertIn("SCENARIOCRAFT_CONFIG_FILE:-./demo/family-config.yml", compose)
        self.assertIn("SCENARIOCRAFT_BEDROCK_PORT:-19132", bedrock)
        self.assertIn("MODRINTH_PROJECTS: geyser:", bedrock)
        self.assertIn("projects/floodgate/", bedrock)

    def test_macos_path_owns_key_sync_service_and_external_probe(self):
        script = (ROOT / "demo" / "family-server.sh").read_text(encoding="utf-8")

        self.assertIn('docker-compose.bedrock.yml', script)
        self.assertIn('SCENARIOCRAFT_BEDROCK_PORT=19133 compose_cmd up', script)
        self.assertIn('docker cp "$container_id:/data/plugins/floodgate/key.pem"', script)
        self.assertIn('test -s /data/plugins/floodgate/key.pem', script)
        self.assertIn('[[ ! -s "$runtime_dir/key.pem" ]]', script)
        self.assertIn('launchctl bootstrap', script)
        self.assertIn('rm -f "$plist"', script)
        self.assertIn('-iUDP:19132', script)
        self.assertIn('version "21([.]|\\")', script)
        self.assertIn('demo/check-bedrock.sh', script)

    def test_status_exits_when_bedrock_probe_fails(self):
        script = (ROOT / "demo" / "family-server.sh").read_text(encoding="utf-8")
        status_case = script.split('    status)\n', 1)[1].split('        ;;', 1)[0]

        self.assertIn('else', status_case)
        self.assertIn('exit 1', status_case)

    def test_automated_demos_keep_the_short_test_configuration(self):
        for relative_path in ("demo/run-headless.sh", "e2e/run-proof-round.sh"):
            script = (ROOT / relative_path).read_text(encoding="utf-8")
            self.assertIn(
                "SCENARIOCRAFT_CONFIG_FILE=./demo/plugin-config.yml", script
            )


if __name__ == "__main__":
    unittest.main()
