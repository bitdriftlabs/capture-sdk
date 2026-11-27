// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { cp, mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Run when preparing the npm release, never during a customer's npm install.
const root = fileURLToPath(new URL('../', import.meta.url));
const packageDir = path.join(root, 'packages/react-native');
const { captureSdkVersion: version } = JSON.parse(
  await readFile(path.join(packageDir, 'package.json')),
);

if (!/^\d+\.\d+\.\d+(?:-[\w.-]+)?$/.test(version)) {
  throw new Error(`Invalid Capture SDK version: ${version}`);
}

const url = `https://dl.bitdrift.io/sdk/ios/capture-${version}/Capture.zip`;

async function download(address) {
  const response = await fetch(address);
  if (!response.ok) throw new Error(`${address}: HTTP ${response.status}`);
  return Buffer.from(await response.arrayBuffer());
}

const [archive, checksum] = await Promise.all([
  download(url),
  download(`${url}.sha256`),
]);
const expected = checksum.toString().trim().split(/\s+/)[0];
const sha256 = createHash('sha256').update(archive).digest('hex');

if (!/^[a-f0-9]{64}$/.test(expected) || sha256 !== expected) {
  throw new Error(`Capture ${version} archive checksum mismatch`);
}

const staging = await mkdtemp(path.join(tmpdir(), 'capture-rn-ios-'));

try {
  const zip = path.join(staging, 'Capture.zip');
  await writeFile(zip, archive);
  execFileSync('unzip', ['-q', zip, '-d', path.join(staging, 'extracted')]);
  const extracted = path.join(staging, 'extracted');
  // Fail before replacing an existing bundle if the distribution is incomplete.
  await readFile(path.join(extracted, 'Capture.xcframework/Info.plist'));
  const license = await readFile(path.join(extracted, 'LICENSE'));
  if (!license.length)
    throw new Error('Capture distribution has an empty license');
  const vendor = path.join(packageDir, 'ios/vendor');
  await mkdir(path.dirname(vendor), { recursive: true });
  const prepared = path.join(staging, 'vendor');
  await mkdir(prepared);
  await cp(
    path.join(extracted, 'Capture.xcframework'),
    path.join(prepared, 'Capture.xcframework'),
    { recursive: true },
  );
  await writeFile(path.join(prepared, 'LICENSE'), license);
  await writeFile(
    path.join(prepared, 'capture.json'),
    `${JSON.stringify({ version, url, sha256 }, null, 2)}\n`,
  );
  await rm(vendor, { recursive: true, force: true });
  // cp also works when the system temporary directory is on a different volume.
  await cp(prepared, vendor, { recursive: true });
  console.log(
    `Bundled Capture ${version} (${archive.length} downloaded bytes; SHA-256 ${sha256})`,
  );
} finally {
  await rm(staging, { recursive: true, force: true });
}
