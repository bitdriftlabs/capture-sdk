// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { init, error, warn, info, debug, trace } from './Logger';

describe('init/log', () => {
  it('should initialize', () => {
    const result = init('1234', { url: 'http://bitdrift.io' });
    expect(result).toBeDefined();
  });
});


describe('log', () => {
  beforeAll(() => {
    init('foobar', { url: 'http://bitdrift.io' });
  });

  it('should work', () => {
    error('my message', { key1: 'value1', value2: 123, value3: true });
    error('my message');

    warn('my message', { key1: 'value1', value2: 123, value3: true });
    warn('my message');

    info('my message', { key1: 'value1', value2: 123, value3: true });
    info('my message');

    debug('my message', { key1: 'value1', value2: 123, value3: true });
    debug('my message');

    trace('my message', { key1: 'value1', value2: 123, value3: true });
    trace('my message');
  });
});
