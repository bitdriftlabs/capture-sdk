// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

const fs = require('fs');
const path = require('path');

const [, , target, output] = process.argv;

if (!target) {
  console.error('Please provide a target package.json file');
  process.exit(1);
}

if (!output) {
  console.error('Please provide an output directory');
  process.exit(1);
}

/**
 * Sync implicit dependencies from target package.json to output package.json
 */

const rootPkg = require(path.resolve(process.cwd(), 'package.json'));
const targetPkg = require(path.resolve(process.cwd(), target));
const outputPkg = require(path.resolve(process.cwd(), output, 'package.json'));

outputPkg.dependencies = {
  ...(outputPkg.dependencies || {}),
  ...targetPkg.implicitDependencies.reduce(
    (acc, dep) => ({
      ...acc,
      [dep]: rootPkg.dependencies[dep] ?? rootPkg.devDependencies[dep],
    }),
    {}
  ),
};

console.log('Copying dependencies to', output);
fs.writeFileSync(
  path.resolve(process.cwd(), output, 'package.json'),
  JSON.stringify(outputPkg, null, 2)
);
