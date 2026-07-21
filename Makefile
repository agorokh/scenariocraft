.PHONY: ci-fast demo demo-dry-run evals-check evals-release evals-unit site-check

demo:
	./demo/run-headless.sh

demo-dry-run:
	SCENARIOCRAFT_DEMO_DRY_RUN=true ./demo/run-headless.sh

ci-fast: site-check evals-unit
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
