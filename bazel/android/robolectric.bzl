"""Robolectric android-all jars staged for only the SDK levels a test target runs on.

rules_robolectric's `android-all` target stages all 16 supported SDK jars (about 1.95 GB) into
every test action. Robolectric only reads the jar for the SDK each test selects with
`@Config(sdk = [...])`, so test targets list the SDK levels they use and get just those jars.
"""

load("@rules_java//java:java_library.bzl", "java_library")

# Instrumented android-all jars from rules_robolectric 4.16.1, keyed by API level. Each repository
# must also be listed in MODULE.bazel's `use_repo(robolectric_repositories, ...)`.
ROBOLECTRIC_SDK_JARS = {
    24: "@org_robolectric_android_all_instrumented_7_0_0_r1_robolectric_r1_i7//file",
    28: "@org_robolectric_android_all_instrumented_9_robolectric_4913185_2_i7//file",
    29: "@org_robolectric_android_all_instrumented_10_robolectric_5803371_i7//file",
    30: "@org_robolectric_android_all_instrumented_11_robolectric_6757853_i7//file",
    31: "@org_robolectric_android_all_instrumented_12_robolectric_7732740_i7//file",
    33: "@org_robolectric_android_all_instrumented_13_robolectric_9030017_i7//file",
    34: "@org_robolectric_android_all_instrumented_14_robolectric_10818077_i7//file",
    35: "@org_robolectric_android_all_instrumented_15_robolectric_13954326_i7//file",
}

def robolectric_android_all(name, sdks):
    """Declares `name` (a java_library to depend on) and `name + ".properties"`.

    Pass the properties file as the test's `robolectric_properties_file`. A test that selects an SDK
    missing from `sdks`, or that has no `@Config(sdk = ...)` (Robolectric then picks the newest SDK
    it knows), fails at runtime because Robolectric cannot resolve that android-all jar.

    Args:
      name: Name of the java_library carrying the jars and properties file as runfiles.
      sdks: API levels to stage; each must be a key of ROBOLECTRIC_SDK_JARS.
    """
    for sdk in sdks:
        if sdk not in ROBOLECTRIC_SDK_JARS:
            fail("Robolectric SDK {} is not in ROBOLECTRIC_SDK_JARS".format(sdk))

    jars = [ROBOLECTRIC_SDK_JARS[sdk] for sdk in sorted(sdks)]

    # Robolectric's PropertiesDependencyResolver resolves entries relative to the properties file's
    # directory, <runfiles>/_main/<package>, so climb to the runfiles root and into the jar's repo.
    package = native.package_name()
    to_runfiles_root = "../" * ((len(package.split("/")) if package else 0) + 1)
    native.genrule(
        name = name + "_properties",
        testonly = True,
        srcs = jars,
        outs = [name + ".properties"],
        cmd = """
for jar in $(SRCS); do
  version=$$(basename "$$jar" .jar | sed 's/^android-all-instrumented-//')
  echo "org.robolectric\\\\:android-all-instrumented\\\\:$$version={root}$$(echo "$$jar" | sed 's|^external/||')"
done > $@
""".format(root = to_runfiles_root),
    )

    java_library(
        name = name,
        testonly = True,
        data = jars + [name + ".properties"],
    )
