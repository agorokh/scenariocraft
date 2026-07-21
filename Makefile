.PHONY: bedrock-compose-check bedrock-compose-smoke bedrock-probe-check ci-fast demo demo-dry-run docs-check evals-check evals-release evals-unit geyser-config-seed-check site-check

demo:
	./demo/run-headless.sh

demo-dry-run:
	SCENARIOCRAFT_DEMO_DRY_RUN=true ./demo/run-headless.sh

ci-fast: site-check docs-check evals-unit geyser-config-seed-check bedrock-probe-check
	@if command -v sha256sum >/dev/null 2>&1; then \
		sha256sum --check gradle/wrapper/gradle-wrapper.jar.sha256; \
	else \
		shasum -a 256 --check gradle/wrapper/gradle-wrapper.jar.sha256; \
	fi
	./gradlew build --no-daemon
	./evals/run.sh --dry-run --allow-synthetic-only

evals-unit:
	python3 -m unittest discover -s evals/tests -p 'test_*.py'

evals-check: evals-unit
	./evals/run.sh --dry-run --allow-synthetic-only

evals-release: evals-unit
	./evals/run.sh --dry-run

site-check:
	python3 -m unittest discover -s scripts -p 'test_*.py'
	python3 scripts/site_check.py
	grep -Fq 'One household, every device' site/index.html
	grep -Fq 'no game-client captures' site/index.html

docs-check:
	grep -Fq 'One household, every device' README.md
	grep -Fq 'Docker Desktop on macOS' demo/README.md
	test -x demo/check-bedrock.sh
	test -x demo/smoke-bedrock-compose.sh
	test -x demo/seed-geyser-config.sh
	test -f docker-compose.smoke.yml

geyser-config-seed-check:
	./demo/test-geyser-config-seed.sh

bedrock-probe-check:
	./demo/test-check-bedrock.py

bedrock-compose-check:
	./demo/test-bedrock-compose.sh

bedrock-compose-smoke:
	./demo/smoke-bedrock-compose.sh
