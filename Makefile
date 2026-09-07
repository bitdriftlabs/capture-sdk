.ONESHELL: # support multiline commands
# support loop constructs
SHELL=bash
FORMAT_MAKE_FLAGS=--no-print-directory --silent

BITDRIFT_MAESTRO_VERSION ?= 2.11.0
BITDRIFT_MAESTRO_DIR ?= $(CURDIR)/.tools/bitdrift-maestro/$(BITDRIFT_MAESTRO_VERSION)
MAESTRO_BIN ?= $(BITDRIFT_MAESTRO_DIR)/bin/maestro
IOS_PHYSICAL_SMOKE_APP_IPA ?= $(CURDIR)/bazel-bin/examples/swift/hello_world/hello_world_app.ipa
APPLE_TEAM_ID ?= $(shell grep -E '^[[:space:]]*build[[:space:]]+--repo_env=APPLE_TEAM_ID=' .bazelrc.local 2>/dev/null | sed 's/.*APPLE_TEAM_ID=//' | awk '{ print $$1 }' | tail -n 1)
IOS_CRASH_CATALOG_IDS := crash.ForceUnwrapCrash crash.ArrayOutOfBoundsCrash crash.StackOverflowCrash crash.fatal-error crash.AssertionCrash crash.PreconditionCrash crash.IntegerOverflowCrash crash.DivisionByZeroCrash crash.AbortCrash crash.NullPointerCrash crash.SIGSEGVCrash crash.SIGBUSCrash crash.SIGILLCrash crash.SIGFPECrash crash.StackSmashCrash crash.MainThreadSyncDispatch crash.WatchdogSceneUpdateCrash crash.WatchdogProcessExitCrash crash.AsyncSafeThreadCrash crash.ObjCExceptionCrash crash.CXXExceptionCrash crash.ObjCMsgSendCrash crash.UnrecognizedSelectorCrash crash.KVOCrash crash.ReleasedObjectCrash crash.CorruptMallocCrash crash.OOMKillCrash crash.BackgroundOOMKillCrash

-include .maestro.local.mk

.PHONY: build
build:
	echo "This command exists as CI expects BUILD command to be available"

.PHONY: install-bitdrift-maestro
install-bitdrift-maestro:
	BITDRIFT_MAESTRO_VERSION="$(BITDRIFT_MAESTRO_VERSION)" \
	BITDRIFT_MAESTRO_DIR="$(BITDRIFT_MAESTRO_DIR)" \
	./tools/maestro/install_bitdrift_maestro.sh

.PHONY: build-ios-smoke-app
build-ios-smoke-app:
	./bazelw build --config=release-ios --cpu=ios_arm64 //examples/swift/hello_world:hello_world_app

.PHONY: list-ios-devices
list-ios-devices:
	python3 ./tools/maestro/resolve_ios_hardware_udid.py

.PHONY: test-ios-smoke
test-ios-smoke: install-bitdrift-maestro build-ios-smoke-app
	MAESTRO_BIN="$(MAESTRO_BIN)" \
	APPLE_TEAM_ID="$(APPLE_TEAM_ID)" \
	IOS_DEVICE_ID="$(IOS_DEVICE_ID)" \
	IOS_PHYSICAL_SMOKE_APP_IPA="$(IOS_PHYSICAL_SMOKE_APP_IPA)" \
	./tools/maestro/run_ios_physical_smoke.sh

.PHONY: test-ios-crash-catalog
test-ios-crash-catalog: install-bitdrift-maestro build-ios-smoke-app
	MAESTRO_BIN="$(MAESTRO_BIN)" \
	APPLE_TEAM_ID="$(APPLE_TEAM_ID)" \
	IOS_DEVICE_ID="$(IOS_DEVICE_ID)" \
	IOS_PHYSICAL_SMOKE_APP_IPA="$(IOS_PHYSICAL_SMOKE_APP_IPA)" \
	IOS_MAESTRO_FLOW="$(CURDIR)/tools/maestro/ios-physical-crash-catalog.yaml" \
	IOS_MAESTRO_CRASH_IDS="$(IOS_CRASH_CATALOG_IDS)" \
	./tools/maestro/run_ios_physical_smoke.sh

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
	buildifier -warnings all -lint=fix -r .

.PHONY: lint-yaml
lint-yaml:
	taplo lint $$(git ls-files -- '*.toml')

.PHONY: lint-shell
lint-shell:
	find ./tools -type f \( -name '*.sh' \) | xargs shellcheck -x && find ./ci -type f \( -name '*.sh' \) | xargs shellcheck -x

.PHONY: fix-yaml
fix-yaml:
	taplo fmt

.PHONY: fix-swiftlint
fix-swiftlint:
# tools/lint:fix doesn't warn about all docstring violations.
# WebViewBridgeScript.swift is auto-generated; swiftlint's --format flag reindents it via
# SourceKit regardless of `excluded` in .swiftlint.yml, so it's passed an explicit file list here.
	swiftlint --quiet --fix --format --force-exclude $$(git ls-files -- '*.swift' ':!:platform/swift/source/integrations/webview/WebViewBridgeScript.swift')

.PHONY: lint-docstrings
lint-docstrings:
# Format doc strings first (by running tools/lint:fix) and validate whether any violations remain.
	./bazelw run tools/lint:lint-docstrings

.PHONY: fix-swift
fix-swift: fix-swiftlint lint-docstrings

.PHONY: format-bazel
format-bazel:
	+@$(MAKE) $(FORMAT_MAKE_FLAGS) ktlint && \
	$(MAKE) $(FORMAT_MAKE_FLAGS) rustfmt

.PHONY: format
format:
	+@$(MAKE) $(FORMAT_MAKE_FLAGS) buildifier && \
	$(MAKE) $(FORMAT_MAKE_FLAGS) -j4 format-bazel lint-shell fix-swiftlint lint-yaml && \
	$(MAKE) $(FORMAT_MAKE_FLAGS) lint-docstrings

# Use repin when you get Error: Digests do not match
.PHONY: repin
repin:
	# rules_rs resolves crates from Cargo.lock and records its facts in MODULE.bazel.lock.
	./bazelw mod tidy

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
