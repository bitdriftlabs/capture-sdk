"""Rules for creating an aar for distribution including native libraries"""
# Copied from https://github.com/envoyproxy/envoy-mobile/blob/main/bazel/android_artifacts.bzl

load("@google_bazel_common//tools/maven:pom_file.bzl", "pom_file")
load("@rules_android//android:rules.bzl", "android_binary")
load("@rules_cc//cc:defs.bzl", "cc_library")
load("@rules_java//java:defs.bzl", "java_binary")
load("//bazel/android:dokka.bzl", "sources_javadocs")

# This file is based on https://github.com/aj-michael/aar_with_jni which is
# subject to the following copyright and license:
#
# MIT License
#
# Copyright (c) 2019 Adam Michael
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
def android_artifacts(name, android_library, manifest, archive_name, native_deps = [], proguard_rules = "", visibility = [], excluded_artifacts = []):
    """Create an aar including a native library

    NOTE: The bazel android_library's implicit aar output doesn't flatten its transitive
    dependencies. Additionally, when using the kotlin rules, the kt_android_library rule
    creates a few underlying libraries which makes the declared sources and dependencies
    a transitive dependency on the resulting android_library. The result of this is that
    the classes.jar in the resulting aar will be empty. In order to workaround this issue,
    this rule manually constructs the aar.


    This macro exposes two gen rules:
    1. `{name}` which outputs the aar, sources.jar, javadoc.jar.
    2. `{name}_aar_only` which outputs the aar.

    Args:
        name: The name of the underlying gen rule.
        android_library: The android library target.
        manifest: The android manifest to include in the aar
        archive_name: The basename of the output aar
        native_deps: The native dependency targets.
        proguard_rules: The proguard rules used for the aar.
        visibility: The visibility of the underlying gen rule.
        excluded_artifacts: The dependencies to avoid adding to the .pom xml even if they appear as a transitive dependency.
    """

    # Create the aar
    classes_jar = _create_classes_jar(name, manifest, android_library)
    jni_archive = _create_jni_library(name, native_deps)
    aar_output = _create_aar(name, classes_jar, jni_archive, proguard_rules, manifest, visibility)

    native.filegroup(
        name = name + "_objdump_collector",
        srcs = native_deps,
        output_group = "objdump",
        visibility = ["//visibility:public"],
    )

    # Generate other needed files for a maven publish
    sources_name, javadocs_name = _create_sources_javadocs(name, android_library)
    pom_name = _create_pom_xml(name, android_library, visibility, excluded_artifacts)
    native.genrule(
        name = name + "_with_artifacts",
        srcs = [
            aar_output,
            pom_name,
            sources_name + "_deploy-src.jar",
            javadocs_name,
        ],
        outs = [
            archive_name + ".aar",
            archive_name + ".pom",
            archive_name + "-sources.jar",
            archive_name + "-javadoc.jar",
        ],
        visibility = visibility,
        cmd = """
        # Set source variables
        set -- $(SRCS)
        src_aar=$$1
        src_pom_xml=$$2
        src_sources_jar=$$3
        src_javadocs=$$4

        # Set output variables
        set -- $(OUTS)
        out_aar=$$1
        out_pom_xml=$$2
        out_sources_jar=$$3
        out_javadocs=$$4

        echo "Outputting .pom xml, sources.jar, and javadocs.jar..."
        cp $$src_aar $$out_aar
        cp $$src_pom_xml $$out_pom_xml
        cp $$src_sources_jar $$out_sources_jar
        cp $$src_javadocs $$out_javadocs
        echo "Finished!"
        """,
    )

def _create_aar(name, classes_jar, jni_archive, proguard_rules, manifest, visibility):
    """This macro rule manually creates an aar artifact.

    The underlying gen rule does the following:
    1. Create the final aar manifest file.
    2. Unzips the apk file generated by the `jni_archive_name` into a temporary directory.
    3. Renames the `lib` directory to `jni` directory since the aar requires the so files
       to be in the `jni` directory.
    4. Copy the android binary `jar` output from the `android_binary_name` as `classes.jar`.
    5. Copy the proguard rules specified in the macro parameters.
    6. Override the apk's aar with a generated one.
    7. Zip everything in the temporary directory into the output.

    Args:
        name: Name of the aar generation rule.
        classes_jar: The classes.jar file which contains all the kotlin/java classes.
        jni_archive: The apk with the desired jni libraries.
        proguard_rules: The proguard.txt file.
        manifest: the Androidmanifest.xml to bundle with the aar
        visibility: The bazel visibility for the underlying rule.
    """
    aar_output = name + "_local.aar"

    native.genrule(
        name = name,
        outs = [aar_output],
        srcs = [
            classes_jar,
            jni_archive,
            manifest,
            proguard_rules,
        ],
        cmd = """
        # Set source variables
        set -- $(SRCS)
        src_classes_jar=$$1
        src_jni_archive_apk=$$2
        src_manifest_xml=$$3
        src_proguard_txt=$$4

        original_directory=$$PWD

        echo "Constructing aar..."
        final_dir=$$(mktemp -d)
        cp $$src_classes_jar $$final_dir/classes.jar
        cd $$final_dir
        unzip $$original_directory/$$src_jni_archive_apk > /dev/null
        if [[ -d lib ]]; then
            mv lib jni
        else
            echo "No jni directory found"
        fi
        cp $$original_directory/$$src_proguard_txt ./proguard.txt
        cp $$original_directory/$$src_manifest_xml AndroidManifest.xml
        zip -r tmp.aar * > /dev/null
        cp tmp.aar $$original_directory/$@
        """,
        visibility = visibility,
    )

    return aar_output

def _create_jni_library(name, native_deps = []):
    """Creates an apk containing the jni so files.

    Args:
        name: The name of the top level macro.
        native_deps: The list of native dependency targets.
    """
    cc_lib_name = name + "_jni_interface_lib"
    jni_archive_name = name + "_jni"

    # Create a dummy manifest file for our android_binary
    native.genrule(
        name = name + "_binary_manifest_generator",
        outs = [name + "_generated_AndroidManifest.xml"],
        cmd = """cat > $(OUTS) <<EOF {}EOF""".format(_manifest("does.not.matter")),
    )

    # This outputs {jni_archive_name}_unsigned.apk which will contain the base files for our aar
    android_binary(
        name = jni_archive_name,
        manifest = name + "_generated_AndroidManifest.xml",
        custom_package = "does.not.matter",
        srcs = [],
        deps = [cc_lib_name],
    )

    # We wrap our native so dependencies in a cc_library because android_binaries
    # require a library target as dependencies in order to generate the appropriate
    # architectures in the directory `lib/`
    cc_library(
        name = cc_lib_name,
        srcs = native_deps,
        linkstatic = True,
    )

    return jni_archive_name + "_unsigned.apk"

def _create_classes_jar(name, manifest, android_library):
    """Creates the classes.jar which contains all the kotlin/java classes

    Args:
        name: The name of the top level macro
        manifest: The manifest file used to create the initial apk
        android_library: The android library target
    """
    android_binary_name = name + "_bin"

    # This jar has all the classes needed for our aar and will be our `classes.jar`
    android_binary(
        name = android_binary_name,
        manifest = manifest,
        custom_package = "does.not.matter",
        srcs = [],
        deps = [android_library],
    )

    # Extract the files we care to propagate from the deploy JAR:
    # - .class files coming from this project, with no third party dependencies included
    # - .kotlin_modules pertaining to the project. We filter in only them by looking for
    #   files that look like META-INF/core_platform_jvm-capture_network_lib.kotlin_module.
    native.genrule(
        name = name + "_classes_jar",
        outs = [name + "_classes.jar"],
        srcs = [android_binary_name + "_deploy.jar"],
        cmd = """
        original_directory=$$PWD
        classes_dir=$$(mktemp -d)
        echo "Creating classes.jar from $(SRCS)"
        pushd $$classes_dir
        unzip $$original_directory/$(SRCS) "META-INF/platform*" io/bitdrift/capture/* > /dev/null
        zip -r classes.jar * > /dev/null
        popd
        cp $$classes_dir/classes.jar $@
        """,
    )

    return name + "_classes.jar"

def _create_pom_xml(name, android_library, visibility, excluded_artifacts):
    """Creates a pom xml associated with the android_library target.

    Args:
        name: The name of the top level macro.
        android_library: The android library to generate a pom xml for.
        visibility: The bazel visibility for the underlying rule.
    """
    pom_name = name + "_pom_xml"

    # This is for the pom xml. It has a public visibility since this can be accessed in the root BUILD file
    pom_file(
        name = pom_name,
        targets = [android_library],
        visibility = visibility,
        template_file = "//bazel:pom_template.xml",
        excluded_artifacts = excluded_artifacts,
    )

    return pom_name

def _create_sources_javadocs(name, android_library):
    """Creates the sources.jar and javadocs.jar for the provided android library.

    This rule generates a sources jar first using a proxy java_binary's result and then uses
    kotlin/dokka's CLI tool to generate javadocs from the sources.jar.

    Args:
        name: The name of the top level macro.
        android_library: The android library which to extract the sources and javadocs.
    """
    sources_name = name + "_android_sources_jar"
    javadocs_name = name + "_android_javadocs"

    # This implicitly outputs {name}_deploy-src.jar which is the sources jar
    java_binary(
        name = sources_name,
        runtime_deps = [android_library],
        main_class = "Capture",
    )

    # This takes all the source files from the source jar and creates a javadoc.jar from it
    sources_javadocs(
        name = javadocs_name,
        sources_jar = sources_name + "_deploy-src.jar",
    )

    return sources_name, javadocs_name

def _manifest(package_name):
    """Helper function to create an appropriate manifest with a provided package name.

    Args:
        package_name: The package name used in the manifest file.
    """
    return """
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="{}" >

    <uses-sdk
            android:minSdkVersion="24"
            android:targetSdkVersion="35"/>
</manifest>
""".format(package_name)
