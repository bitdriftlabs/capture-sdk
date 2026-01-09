// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

/* eslint-disable no-useless-escape */

import { logInternal } from './log';
import { error } from './index';
import Native from './NativeBdReactNative';

jest.mock('react-native', () => ({
  TurboModuleRegistry: {
    getEnforcing: jest.fn().mockReturnValue({
      log: jest.fn(),
      init: jest.fn(),
    }),
  },
  NativeModules: {},
  Platform: {
    select: jest.fn(),
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

describe('error', () => {
  beforeEach(() => {
    (Native.log as jest.Mock) = jest.fn();
  });

  it('should log error with just message', () => {
    error('Simple error message');

    expect(Native.log).toHaveBeenCalledWith(
      4,
      'Simple error message',
      undefined
    );
  });

  it('should log error with message and fields', () => {
    error('Error message', undefined, { userId: '123' });

    expect(Native.log).toHaveBeenCalledWith(
      4,
      'Error message',
      { userId: '123' }
    );
  });

  it('should log error with Error object', () => {
    const err = new Error('Technical error');
    err.name = 'TestError';

    error('User-friendly message', err);

    expect(Native.log).toHaveBeenCalledWith(
      4,
      'User-friendly message',
      {
        _error: 'TestError',
        _error_details: 'Technical error',
      }
    );
  });

  it('should log error with fields and Error object', () => {
    const err = new Error('Technical error');
    err.name = 'TestError';

    error('User-friendly message', err, { userId: '123', action: 'submit' });

    expect(Native.log).toHaveBeenCalledWith(
      4,
      'User-friendly message',
      {
        userId: '123',
        action: 'submit',
        _error: 'TestError',
        _error_details: 'Technical error',
      }
    );
  });

  it('should handle null Error', () => {
    error('Message with null error', null, { context: 'test' });

    expect(Native.log).toHaveBeenCalledWith(
      4,
      'Message with null error',
      { context: 'test' }
    );
  });

  it('should handle error with empty message', () => {
    const err = new Error();
    err.name = 'EmptyError';

    error('Error occurred', err);

    expect(Native.log).toHaveBeenCalledWith(
      4,
      'Error occurred',
      {
        _error: 'EmptyError',
      }
    );
  });

  it('should handle error without name', () => {
    const err = new Error('Message only');
    Object.defineProperty(err, 'name', { value: '' });

    error('Operation failed', err);

    expect(Native.log).toHaveBeenCalledWith(
      4,
      'Operation failed',
      {
        _error: 'Error',
        _error_details: 'Message only',
      }
    );
  });
});
