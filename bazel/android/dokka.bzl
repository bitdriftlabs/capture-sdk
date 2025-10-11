load("@rules_java//java/common:java_common.bzl", "java_common")

def _sources_javadocs_impl(ctx):
    javabase = ctx.attr._javabase[java_common.JavaRuntimeInfo]
    plugins_classpath = ";".join([
        ctx.file._dokka_analysis_kotlin_descriptors_jar.path,
        ctx.file._dokka_base_jar.path,
        ctx.file._freemarker_jar.path,
        ctx.file._kotlinx_html_jar.path,
    ])
    output_jar = ctx.actions.declare_file("{}.jar".format(ctx.attr.name))

    ctx.actions.run_shell(
        tools = [ctx.file._zipper],
        command = """
        set -euxo pipefail  # Added -x for printing commands

        java=$1
        dokka_cli_jar=$2
        plugin_classpath=$3
        sources_jar=$4
        output_jar=$5
        zipper="$PWD/$6"

        sources_dir=$(mktemp -d)
        tmp_dir=$(mktemp -d)
        trap 'rm -rf "$sources_dir" "$tmp_dir"' EXIT

        unzip $sources_jar -d $sources_dir > /dev/null

        $java \
            -jar $dokka_cli_jar \
            -pluginsClasspath $plugin_classpath \
            -moduleName "Capture" \
            -sourceSet "-src $sources_dir -noStdlibLink -noJdkLink" \
            -outputDir $tmp_dir > /dev/null \
            -pluginsConfiguration "org.jetbrains.dokka.base.DokkaBase={\\"footerMessage\\": \\"\\u00A9 2025 bitdrift, Inc.\\", \\"separateInheritedMembers\\": true}"

        original_directory=$PWD
        cd $tmp_dir
        find . -type f -print | sed 's#^\\./##' | sort > $original_directory/filelist.txt
        "$zipper" cC "$original_directory/$output_jar" @$original_directory/filelist.txt
        """,
        arguments = [
            javabase.java_executable_exec_path,
            ctx.file._dokka_cli_jar.path,
            plugins_classpath,
            ctx.file.sources_jar.path,
            output_jar.path,
            ctx.file._zipper.path,
        ],
        inputs = [
            ctx.file._dokka_analysis_kotlin_descriptors_jar,
            ctx.file._dokka_base_jar,
            ctx.file._dokka_cli_jar,
            ctx.file._freemarker_jar,
            ctx.file._kotlinx_html_jar,
            ctx.file.sources_jar,
        ] + ctx.files._javabase,
        outputs = [output_jar],
        mnemonic = "BitdriftDokka",
        progress_message = "Generating javadocs...",
    )

    return [
        DefaultInfo(files = depset([output_jar])),
    ]

sources_javadocs = rule(
    implementation = _sources_javadocs_impl,
    attrs = {
        "sources_jar": attr.label(allow_single_file = True),
        "_dokka_analysis_kotlin_descriptors_jar": attr.label(
            default = "@maven//:org_jetbrains_dokka_analysis_kotlin_descriptors",
            allow_single_file = True,
        ),
        "_dokka_base_jar": attr.label(
            default = "@maven//:org_jetbrains_dokka_dokka_base",
            allow_single_file = True,
        ),
        "_dokka_cli_jar": attr.label(
            default = "@maven//:org_jetbrains_dokka_dokka_cli",
            allow_single_file = True,
        ),
        "_freemarker_jar": attr.label(
            default = "@maven//:org_freemarker_freemarker",
            allow_single_file = True,
        ),
        # Java Runtime is required
        "_javabase": attr.label(
            default = Label("@bazel_tools//tools/jdk:current_java_runtime"),
            allow_files = True,
            providers = [java_common.JavaRuntimeInfo],
        ),
        "_kotlinx_html_jar": attr.label(
            default = "@maven//:org_jetbrains_kotlinx_kotlinx_html_jvm",
            allow_single_file = True,
        ),
        "_zipper": attr.label(
            default = Label("//bazel:zipper"),
            allow_single_file = True,
            cfg = "exec",
        ),
    },
)
