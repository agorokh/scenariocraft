.PHONY: ci-fast demo demo-dry-run evals-check evals-unit site-check

demo:
	./demo/run-headless.sh

demo-dry-run:
	SCENARIOCRAFT_DEMO_DRY_RUN=true ./demo/run-headless.sh

ci-fast: site-check evals-check
	@if command -v sha256sum >/dev/null 2>&1; then \
		sha256sum --check gradle/wrapper/gradle-wrapper.jar.sha256; \
	else \
		shasum -a 256 --check gradle/wrapper/gradle-wrapper.jar.sha256; \
	fi
	./gradlew build --no-daemon

evals-unit:
	python3 -m unittest discover -s evals/tests -p 'test_*.py'

evals-check: evals-unit
	./evals/run.sh --dry-run

site-check:
	test -f site/index.html
	test -f site/styles.css
	test -f site/assets/branding/speed-build-logo.png
	test "$$(grep -c '<article class="step' site/index.html)" -eq 7
	grep -Fq 'name &amp; logo by our 10-year-old designer, working with ChatGPT' site/index.html
	grep -Fq 'NOT AN OFFICIAL MINECRAFT PRODUCT' site/index.html
	! grep -Eiq '(src|href)="(https?:)?//' site/index.html
	! grep -Eiq '@import|url\([^)]*(https?:)?//' site/styles.css
