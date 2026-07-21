.PHONY: bedrock-compose-check bedrock-compose-smoke bedrock-probe-check ci-fast demo demo-dry-run docs-check evals-check evals-release evals-unit geyser-config-seed-check proof-round proof-check renderer-dist site-check verify-wrapper

demo:
	./demo/run-headless.sh

demo-dry-run:
	SCENARIOCRAFT_DEMO_DRY_RUN=true ./demo/run-headless.sh

proof-round:
	./e2e/run-proof-round.sh

ci-fast: site-check proof-check docs-check evals-unit geyser-config-seed-check bedrock-probe-check
	./gradlew build --no-daemon
	./evals/run.sh --dry-run --allow-synthetic-only

verify-wrapper:
	@if command -v sha256sum >/dev/null 2>&1; then \
		sha256sum --check gradle/wrapper/gradle-wrapper.jar.sha256; \
	else \
		shasum -a 256 --check gradle/wrapper/gradle-wrapper.jar.sha256; \
	fi

renderer-dist: verify-wrapper
	./gradlew :renderer:installDist --no-daemon

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

proof-check: renderer-dist
	command -v node >/dev/null
	node --test e2e/round-driver/test/*.test.mjs
	SCENARIOCRAFT_RENDERER=renderer/build/install/renderer/bin/renderer \
		node e2e/round-driver/assemble.mjs check --site site
	node site/build-round-page.mjs --check site
