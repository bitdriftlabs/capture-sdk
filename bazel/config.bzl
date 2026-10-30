"""Shared configuration for things we don't want to duplicate"""

MINIMUM_IOS_VERSION = "15.0"
MINIMUM_IOS_VERSION_TESTS = "15.0"

# Apple libraries are built both for the macOS host and through iOS transitions.
# Tags such as macos_only do not exclude targets from wildcard builds on Linux.
APPLE_TARGET_COMPATIBILITY = select({
    "@platforms//os:ios": [],
    "@platforms//os:macos": [],
    "//conditions:default": ["@platforms//:incompatible"],
})
