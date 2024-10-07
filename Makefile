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
format: ktlint rustfmt buildifier fix-swift lint-yaml

.PHONY: repin
repin:
	CARGO_BAZEL_REPIN=1 ./bazelw sync --only=crate_index

.PHONY: push-additional-images
push-additional-images:
