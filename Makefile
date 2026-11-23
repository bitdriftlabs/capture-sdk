.ONESHELL: # support multiline commands
# support loop constructs
SHELL=bash
FORMAT_MAKE_FLAGS=--no-print-directory --silent

.PHONY: build
build:
	echo "This command exists as CI expects BUILD command to be available"

.PHONY: ktlint
ktlint:
	./bazelw run //:ktlint_fix_all

.PHONY: rustfmt
rustfmt:
	./bazelw run //:rustfmt

.PHONY: setup-rust-analyzer
setup-rust-analyzer:
	./scripts/setup-rust-analyzer.sh

.PHONY: buildifier
buildifier:
	find . -path './platform/es' -prune -o -type f \( -name '*.bzl' -o -name 'BUILD' -o -name 'BUILD.bazel' -o -name 'WORKSPACE' -o -name 'WORKSPACE.bazel' -o -name 'MODULE.bazel' -o -name 'REPO.bazel' \) -print0 | xargs -0 buildifier -warnings all -lint=fix

.PHONY: lint-yaml
lint-yaml:
	taplo fmt --check $$(git ls-files -- '*.toml' ':(exclude)platform/es')

.PHONY: lint-shell
lint-shell:
	find ./tools -type f \( -name '*.sh' \) | xargs shellcheck -x && find ./ci -type f \( -name '*.sh' \) | xargs shellcheck -x

.PHONY: fix-yaml
fix-yaml:
	taplo fmt

.PHONY: fix-swiftlint
SWIFTLINT ?= swiftlint
fix-swiftlint:
# WebViewBridgeScript.swift is auto-generated; swiftlint's --format flag reindents it via
# SourceKit regardless of `excluded` in .swiftlint.yml, so it's passed an explicit file list here.
	$(SWIFTLINT) --quiet --fix --format --force-exclude $$(git ls-files -- '*.swift' ':(exclude)platform/es' ':!:platform/swift/source/integrations/webview/WebViewBridgeScript.swift')

.PHONY: fix-docstrings
fix-docstrings:
	$(if $(FILE),,$(error Usage: make fix-docstrings FILE=path/to/File.swift))
	./bazelw run //tools/lint:fix-docstrings -- --include "$(FILE)"

.PHONY: lint-docstrings
lint-docstrings:
	./bazelw run //tools/lint:lint-docstrings

.PHONY: fix-swift
fix-swift: fix-swiftlint
	+@$(MAKE) $(FORMAT_MAKE_FLAGS) lint-docstrings

.PHONY: format-bazel
format-bazel:
	+@$(MAKE) $(FORMAT_MAKE_FLAGS) ktlint && \
	$(MAKE) $(FORMAT_MAKE_FLAGS) rustfmt

.PHONY: format
format:
	+@$(MAKE) $(FORMAT_MAKE_FLAGS) check-license && \
	$(MAKE) $(FORMAT_MAKE_FLAGS) buildifier && \
	$(MAKE) $(FORMAT_MAKE_FLAGS) lint-yaml && \
	$(MAKE) $(FORMAT_MAKE_FLAGS) -j4 format-bazel lint-shell fix-swiftlint && \
	$(MAKE) $(FORMAT_MAKE_FLAGS) lint-docstrings

.PHONY: check-license
check-license:
	./ci/check_license.sh

# Use repin when you get Error: Digests do not match, or after changing Maven artifacts in
# MODULE.bazel (Bazel then reports that a Maven lockfile must be regenerated).
.PHONY: repin
repin:
	set -e
	# rules_rs resolves crates from Cargo.lock and records its facts in MODULE.bazel.lock.
	./bazelw mod tidy
	# Refresh the SDK and Android build tool lockfiles.
	./bazelw run --repo_env=REPIN=1 @maven//:pin
	./bazelw run --repo_env=REPIN=1 @rules_android_maven//:pin

.PHONY: push-additional-images
push-additional-images:

REPORT_KT=platform/jvm/capture/src/main/kotlin/io/bitdrift/capture/reports/binformat/v1/Report.kt

.PHONY: gen-flatbuffers
gen-flatbuffers: $(REPORT_KT)

.PHONY: $(REPORT_KT) # ignore timestamp
$(REPORT_KT): ../api/src/bitdrift_public/fbs/issue-reporting/v1/report.fbs ../api/src/bitdrift_public/fbs/common/v1/common.fbs
	@flatc --gen-onefile --kotlin -I ../api/src $^
	@for f in $$(find bitdrift_public -type f); do \
		python3 ci/license_header.py $$f >/dev/null; \
		sed -i '' -E 's/bitdrift_public.[._[:alpha:]]*\.([_[:alpha:]]+)\.v1/io.bitdrift.capture.reports.binformat.v1.\1/g' $$f; \
		DEST=$(@D)/$$(basename $$(dirname $$(dirname $$f)))/$$(basename $$f | awk '{$$1=toupper(substr($$1,0,1))substr($$1,2)}1'); \
		mkdir -p $$(dirname $$DEST); \
		mv $$f $$DEST; \
		echo Generated $$DEST; \
	done

.PHONY: xcframework
xcframework:
	echo "NOTE: --xcode_version is overridden in .bazelrc"
	./bazelw build //:ios_dist
	echo "XCFramework is archived at bazel-bin/Capture.ios.zip"

.PHONY: test-gradle
test-gradle:
	platform/jvm/gradlew :capture:testDebugUnitTest -p platform/jvm

.PHONY: init-local-bazelrc
init-local-bazelrc:
	@if [ ! -f .bazelrc.local ]; then \
		cp .bazelrc.local.example .bazelrc.local; \
		echo "Created .bazelrc.local from .bazelrc.local.example. Please edit it to suit your environment."; \
	else \
		echo ".bazelrc.local already exists. Skipping creation."; \
	fi

.PHONY: fix-ts
fix-ts:
	npm --prefix ./platform/webview run lint:fix
	npm --prefix ./platform/webview run format

.PHONY: build-ts
build-ts:
	npm --prefix ./platform/webview run build
	npm --prefix ./platform/webview run generate

.PHONY: test-ts
test-ts:
	npm --prefix ./platform/webview run test

.PHONY: install-ts-deps
install-ts-deps:
	npm --prefix ./platform/webview ci

.PHONY: swap-local
swap-local:
	./tools/swap_local.sh
	cargo update
	./bazelw mod tidy
