src_dir := ../../../../api
out_dir := "./src/protos/"
protos := $(shell find -L ${src_dir}/src/bitdrift_public/protobuf/ingest/v1 ${src_dir}/thirdparty/validate -name "*.proto")

protos:
	@protoc -I=$(src_dir)/src -I=$(src_dir)/thirdparty --python_out=$(out_dir) --mypy_out=$(out_dir) $(protos)
	@find $(out_dir) -name "*.py" -exec sed -i '' 's/^from \(bitdrift[^ ]*\) import /from protos.\1 import /' {} \;
	@find $(out_dir) -name "*.py" -exec sed -i '' 's/^from \(validate[^ ]*\) import /from protos.\1 import /' {} \;


all: protos
