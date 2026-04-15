.DEFAULT_GOAL := help
.ONESHELL:
SHELL := /usr/bin/env bash
.SHELLFLAGS := -ec

.PHONY: build
build: ## build library
	@./gradlew build

.PHONY: test
test: ## run unit tests
	@./gradlew test

.PHONY: coverage
coverage: ## run tests and open coverage report
	@./gradlew test jacocoTestReport
	@echo "Report: build/reports/jacoco/test/html/index.html"
	@open build/reports/jacoco/test/html/index.html

.PHONY: perf
perf: ## run performance test
	@./gradlew perfTest

.PHONY: publish
publish: ## install library into local .m2/repo
	@./gradlew publishToMavenLocal

# Based on http://marmelab.com/blog/2016/02/29/auto-documented-makefile.html
help: ## print help for each make target
	@grep -hE '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {gsub(/\\n/, "\n" sprintf("%26s", " "));printf "\033[36m%-25s\033[0m %s\n\n", $$1, $$2}'
