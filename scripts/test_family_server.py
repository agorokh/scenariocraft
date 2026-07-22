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
        self.assertIn('geyser_build=1201', script)
        self.assertIn(
            'geyser_sha256=036475e5a1dfea07bd0d2974d117e67fb477df1c47db7c95b90de0638c019d22',
            script,
        )
        self.assertIn('SCENARIOCRAFT_CONFIG_FILE=./demo/family-config.yml', script)
        self.assertIn('export SCENARIOCRAFT_BEDROCK_PORT=19132', script)
        self.assertIn('wait_for_bedrock', script)
        self.assertIn('Geyser did not answer UDP 19132 within 60 seconds.', script)
        self.assertIn('return 0', script)
        self.assertIn("printf '%s\\n' \"$probe_output\"", script)
        self.assertIn('trap cleanup_failed_up EXIT', script)
        self.assertIn('compose_cmd down || true', script)
        self.assertIn('install -d -m 700 "$runtime_dir"', script)
        unload = script.split('unload_macos_geyser() {', 1)[1].split(
            '\n}\n', 1
        )[0]
        port_wait = unload.index('for _ in $(seq 1 20); do', unload.index('rm -f'))
        self.assertGreater(port_wait, unload.index('if [[ "$remove_plist" == true ]]'))

    def test_status_exits_when_bedrock_probe_fails(self):
        script = (ROOT / "demo" / "family-server.sh").read_text(encoding="utf-8")
        status_case = script.split('    status)\n', 1)[1].split('        ;;', 1)[0]

        self.assertIn('else', status_case)
        self.assertIn('exit 1', status_case)

    def test_documented_macos_log_and_status_match_runtime(self):
        readme = (ROOT / "README.md").read_text(encoding="utf-8")
        script = (ROOT / "demo" / "family-server.sh").read_text(encoding="utf-8")

        self.assertIn('.local/geyser/geyser.log', readme)
        self.assertIn('runtime_dir="$repo_dir/.local/geyser"', script)
        self.assertIn('geyser_log="$runtime_dir/geyser.log"', script)
        self.assertIn(
            'Bedrock UDP 19132 answered a RakNet discovery probe.', readme
        )
        self.assertIn(
            'Bedrock UDP 19132 answered a RakNet discovery probe.', script
        )

    def test_automated_demos_keep_the_short_test_configuration(self):
        for relative_path in ("demo/run-headless.sh", "e2e/run-proof-round.sh"):
            script = (ROOT / relative_path).read_text(encoding="utf-8")
            self.assertIn(
                "SCENARIOCRAFT_CONFIG_FILE=./demo/plugin-config.yml", script
            )


if __name__ == "__main__":
    unittest.main()
