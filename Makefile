.PHONY: ci-fast demo demo-dry-run evals-check evals-release evals-unit proof-round proof-check renderer-dist site-check verify-wrapper

demo:
	./demo/run-headless.sh

demo-dry-run:
	SCENARIOCRAFT_DEMO_DRY_RUN=true ./demo/run-headless.sh

proof-round:
	./e2e/run-proof-round.sh

ci-fast: site-check proof-check evals-unit
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

proof-check: renderer-dist
	command -v node >/dev/null
	node --test e2e/round-driver/test/*.test.mjs
	SCENARIOCRAFT_RENDERER=renderer/build/install/renderer/bin/renderer \
		node e2e/round-driver/assemble.mjs check --site site
	node site/build-round-page.mjs --check site
