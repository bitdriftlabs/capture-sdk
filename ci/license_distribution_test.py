# capture-sdk - bitdrift's client SDK
# Copyright Bitdrift, Inc. All rights reserved.
#
# Use of this source code is governed by a source available license that can be found in the
# LICENSE file or at:
# https://polyformproject.org/licenses/strict/1.0.0

import json
import pathlib
import re
import unittest
import xml.etree.ElementTree as ET


ROOT = pathlib.Path(__file__).resolve().parent.parent
NAME = 'PolyForm Strict License 1.0.0'
URL = 'https://polyformproject.org/licenses/strict/1.0.0'


class LicenseDistributionTest(unittest.TestCase):
    def test_bazel_and_gradle_maven_licenses_agree(self):
        root = ET.parse(ROOT / 'bazel/pom_template.xml').getroot()
        namespace = {'m': 'http://maven.apache.org/POM/4.0.0'}
        license = root.find('m:licenses/m:license', namespace)
        self.assertEqual(license.find('m:name', namespace).text, NAME)
        self.assertEqual(license.find('m:url', namespace).text, URL)
        for module in ('capture-plugin', 'capture-apollo', 'capture-timber'):
            contents = (ROOT / f'platform/jvm/{module}/build.gradle.kts').read_text()
            self.assertIn(f'name.set("{NAME}")', contents)
            self.assertIn(f'url.set("{URL}")', contents)

    def test_release_inputs_use_the_canonical_license(self):
        self.assertFalse((ROOT / 'ci/LICENSE').exists())
        build = (ROOT / 'BUILD').read_text()
        self.assertRegex(build, r'name = "license",\s*srcs = \[\s*"LICENSE"')
        release = (ROOT / 'ci/capture_android_release.sh').read_text()
        self.assertNotIn('ci/LICENSE', release)
        self.assertEqual(release.count('"$sdk_repo/LICENSE"'), 3)

    def test_flutter_license_reference_is_resolvable(self):
        path = ROOT / 'platform/capture_flutter/ios/capture_flutter.podspec'
        contents = path.read_text()
        self.assertIn(f":type => '{NAME}'", contents)
        license_file = re.search(r":file => '([^']+)'", contents).group(1)
        self.assertEqual((path.parent / license_file).resolve(), ROOT / 'LICENSE')

    def test_webview_metadata_and_generated_headers_agree(self):
        package = json.loads((ROOT / 'platform/webview/package.json').read_text())
        lock = json.loads((ROOT / 'platform/webview/package-lock.json').read_text())
        self.assertEqual(package['license'], 'SEE LICENSE IN ../../LICENSE')
        self.assertEqual(lock['packages']['']['license'], package['license'])
        self.assertIn(URL, (ROOT / 'platform/webview/scripts/generate-native.ts').read_text())
        for path in (
            'platform/jvm/capture/src/main/kotlin/io/bitdrift/capture/webview/WebViewBridgeScript.kt',
            'platform/swift/source/integrations/webview/WebViewBridgeScript.swift',
        ):
            header = (ROOT / path).read_text().split('// AUTO-GENERATED FILE', 1)[0]
            self.assertIn(URL, header)


if __name__ == '__main__':
    unittest.main()
