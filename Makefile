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
	swiftlint --quiet --fix --format && ./bazelw run tools/lint:lint-docstrings

.PHONY: format
format: lint-shell ktlint rustfmt buildifier fix-swift lint-yaml

.PHONY: repin
repin:
	CARGO_BAZEL_REPIN=true ./bazelw sync --only=crate_index

.PHONY: push-additional-images
push-additional-images:

.PHONY: ios-run-app
ios-run-app:
	./bazelw run --config ci --config release-ios //examples/swift/hello_world:ios_app

.PHONY: ios-build-app
ios-build-app:
	./bazelw build --config ci --config release-ios //examples/swift/hello_world:ios_app

.PHONY: ios-test
ios-test:
	./bazelw test $(shell ./bazelw query 'kind(ios_unit_test, //test/platform/swift/unit_integration/core/...)') --test_tag_filters=macos_only --test_output=errors --config=ci --config=ios

.PHONY: android-build-app
android-build-app:
	./bazelw build --config ci --config release-android --android_platforms=@rules_android//:x86_64 //examples/android:android_app :capture_aar

