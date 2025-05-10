// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

/* eslint-disable no-useless-escape */

import { logInternal } from './log';
import Native from './NativeBdReactNative';

jest.mock('react-native', () => ({
  TurboModuleRegistry: {
    getEnforcing: jest.fn().mockReturnValue({
      log: jest.fn(),
    }),
  },
}));

describe('log', () => {
  beforeEach(() => {
    (Native.log as jest.Mock) = jest.fn();
  });

  test.each`
    fields                                                  | expected
    ${{ key: 'value' }}                                     | ${{ key: 'value' }}
    ${{ key: 1 }}                                           | ${{ key: '1' }}
    ${{ key: 1.4329587 }}                                   | ${{ key: '1.4329587' }}
    ${{ key: true }}                                        | ${{ key: 'true' }}
    ${{ key: {} }}                                          | ${{ key: '{}' }}
    ${{ key: [] }}                                          | ${{ key: '[]' }}
    ${{ key: null }}                                        | ${{ key: 'null' }}
    ${{ key: undefined }}                                   | ${{ key: 'undefined' }}
    ${{ key: { nested: { nested: { nested: 'value' } } } }} | ${{ key: '{"nested":{"nested":{"nested":"value"}}}' }}
    ${{ key: [1, true, 'foobar'] }}                         | ${{ key: '[1,true,\"foobar\"]' }}
  `('calls Native.log with the correct fields', ({ fields, expected }) => {
    logInternal('trace', 'message', fields);

    expect(Native.log).toHaveBeenCalledWith(0, 'message', expected);
  });
});
