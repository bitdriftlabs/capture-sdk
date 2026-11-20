// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

const fs = require('fs');
const { resolve } = require('path');

const [, , bin, target] = process.argv;

if (!bin) {
  console.error('Please provide a target binary');
  process.exit(1);
}

if (!target) {
  console.error('Please provide a target library');
  process.exit(1);
}

const targetBinDir = resolve(process.cwd(), 'dist', target, 'bin', bin);

/**
 * Copy native binary to output directory
 */
if (!fs.existsSync(targetBinDir)) {
  fs.mkdirSync(targetBinDir, {
    recursive: true,
  });
}

fs.copyFile(
  resolve(process.cwd(), 'dist', bin, 'index.node'),
  resolve(targetBinDir, 'index.node'),
  (err) => {
    if (err) {
      console.error(err);
    }
  }
);
