.PHONY: ci-fast demo demo-dry-run site-check

demo:
	./demo/run-headless.sh

demo-dry-run:
	SCENARIOCRAFT_DEMO_DRY_RUN=true ./demo/run-headless.sh

ci-fast: site-check
	@if command -v sha256sum >/dev/null 2>&1; then \
		sha256sum --check gradle/wrapper/gradle-wrapper.jar.sha256; \
	else \
		shasum -a 256 --check gradle/wrapper/gradle-wrapper.jar.sha256; \
	fi
	./gradlew build --no-daemon

site-check:
	python3 site/check.py
