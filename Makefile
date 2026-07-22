.ONESHELL: # support multiline commands
# support loop constructs
SHELL=bash

.PHONY: build
build:
	echo "This command exists as CI expects BUILD command to be available"

.PHONY: ktlint
ktlint:
	./bazelw query 'kind(ktlint_fix, //...)' | xargs -n1 ./bazelw run

.PHONY: rustfmt
rustfmt:
	RUSTFMT_CONFIG=$(pwd)/rustfmt.toml ./bazelw run @rules_rust//:rustfmt

.PHONY: buildifier
buildifier:
	buildifier -warnings all -lint=fix -r .

.PHONY: lint-yaml
lint-yaml:
	taplo lint

.PHONY: lint-shell
lint-shell:
	find ./tools -type f \( -name '*.sh' \) | xargs shellcheck -x && find ./ci -type f \( -name '*.sh' \) | xargs shellcheck -x

.PHONY: fix-yaml
fix-yaml:
	taplo fmt

.PHONY: fix-swift
fix-swift:
# tools/lint:fix doesn't warn about all docstring violations.
# For this reason format doc strings first (by running tools/lint:fix) and validate whether
# any doc string violations are left next (by running tools/lint:lint-docstrings).
# WebViewBridgeScript.swift is auto-generated; swiftlint's --format flag reindents it via
# SourceKit regardless of `excluded` in .swiftlint.yml, so it's passed an explicit file list here.
	swiftlint --quiet --fix --format --force-exclude $$(git ls-files -- '*.swift' ':!:platform/swift/source/integrations/webview/WebViewBridgeScript.swift') && ./bazelw run tools/lint:lint-docstrings

.PHONY: format
format: lint-shell ktlint rustfmt buildifier fix-swift lint-yaml

# Use repin when you get Error: Digests do not match
.PHONY: repin
repin:
	# crate_universe repins during Bazel analysis, so --nobuild keeps this faster while still surfacing real errors.
	CARGO_BAZEL_REPIN=true ./bazelw build --nobuild //platform/shared:platform-shared

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
	echo "NOTE: Make sure you brew install llvm, and follow its instructions to add it to your PATH."
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
