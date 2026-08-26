#!/usr/bin/env python3
# capture-sdk - bitdrift's client SDK
# Copyright Bitdrift, Inc. All rights reserved.
#
# Use of this source code is governed by a source available license that can be found in the
# LICENSE file or at:
# https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.txt

import json
import tempfile
import unittest
from pathlib import Path

from parse_benchmark_results import parse_benchmark_json


class ParseBenchmarkResultsTest(unittest.TestCase):
    def test_excludes_focused_log_field_benchmarks_from_reports(self):
        benchmark_data = {
            "benchmarks": [
                {"name": "logNotMatchedNoFields", "metrics": {}},
                {"name": "logNotMatched1Field", "metrics": {}},
                {"name": "logNotMatched10Fields", "metrics": {}},
                {"name": "logNotMatched50Fields", "metrics": {}},
                {"name": "logNotMatched100Fields", "metrics": {}},
                {"name": "logNotMatched5000Fields", "metrics": {}},
            ]
        }

        with tempfile.TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "benchmark.json"
            path.write_text(json.dumps(benchmark_data))
            results, _ = parse_benchmark_json(path)

        self.assertEqual(set(results), {"logNotMatchedNoFields", "logNotMatched5000Fields"})
