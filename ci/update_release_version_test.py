import base64
import json
import os
import pathlib
import subprocess
import tempfile
import unittest


SCRIPT = pathlib.Path(__file__).with_name('update_release_version.sh').resolve()
SCRATCH = SCRIPT.parent.parent / '.tmp'


class UpdateReleaseVersionTest(unittest.TestCase):
    def setUp(self):
        SCRATCH.mkdir(exist_ok=True)
        self.directory = tempfile.TemporaryDirectory(dir=SCRATCH)
        self.addCleanup(self.directory.cleanup)
        self.root = pathlib.Path(self.directory.name)
        self.state = self.root / 'state.json'
        self.gh = self.root / 'gh'
        self.gh.write_text('''#!/usr/bin/env python3
import json
import os
import pathlib
import sys

path = pathlib.Path(os.environ['GH_TEST_STATE'])
state = json.loads(path.read_text())
args = sys.argv[1:]
state['calls'].append(args)
method = args[2]
count = sum(call[2] == method for call in state['calls'])
path.write_text(json.dumps(state))
if method == 'GET':
    print(state['shas'][min(count - 1, len(state['shas']) - 1)])
elif method == 'PUT':
    sys.exit(1 if count <= state['failures'] else 0)
else:
    sys.exit(2)
''')
        self.gh.chmod(0o755)

    def update(self, shas, failures=0):
        self.state.write_text(json.dumps({'shas': shas, 'failures': failures, 'calls': []}))
        head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=self.root)
        result = subprocess.run(
            ['bash', str(SCRIPT), '0.49.1'], cwd=self.root,
            env=os.environ | {
                'PATH': str(self.root) + os.pathsep + os.environ['PATH'],
                'GH_TEST_STATE': str(self.state),
                'GITHUB_REPOSITORY': 'bitdriftlabs/capture-sdk',
            }, capture_output=True, text=True)
        self.assertEqual(subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=self.root), head)
        return result, json.loads(self.state.read_text())['calls']

    def test_updates_current_main_without_moving_release_checkout(self):
        result, calls = self.update(['latest-main-file-sha'])
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(calls[0], [
            'api', '--method', 'GET',
            'repos/bitdriftlabs/capture-sdk/contents/platform/shared/.sdk_version',
            '-f', 'ref=main', '-q', '.sha'])
        self.assertIn('sha=latest-main-file-sha', calls[1])
        self.assertIn('branch=main', calls[1])
        self.assertIn('content=' + base64.b64encode(b'0.49.1').decode(), calls[1])

    def test_conflict_retries_against_refreshed_main(self):
        result, calls = self.update(['first-sha', 'second-sha'], failures=1)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(len(calls), 4)
        self.assertIn('sha=first-sha', calls[1])
        self.assertIn('sha=second-sha', calls[3])

    def test_persistent_failure_is_bounded_and_reported(self):
        result, calls = self.update(['main-sha'], failures=3)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(len(calls), 6)
        self.assertIn('after 3 attempts', result.stderr)

    def test_existing_version_is_not_committed_again(self):
        sha = subprocess.check_output(
            ['git', 'hash-object', '--stdin'], input=b'0.49.1').decode().strip()
        result, calls = self.update([sha])
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(len(calls), 1)


if __name__ == '__main__':
    unittest.main()
