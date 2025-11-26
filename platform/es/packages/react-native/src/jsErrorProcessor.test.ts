// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { formatRawStack } from './jsErrorProcessor';

describe('formatRawStack', () => {
  test('formats standard stack trace with file path', () => {
    const rawStack = `Error: Test error
    at Component (index.bundle:123:45)
    at render (index.bundle:456:78)`;

    const result = formatRawStack(rawStack);

    expect(result).toBe(
      'at Component (index.bundle:123:45)\nat render (index.bundle:456:78)',
    );
  });

  test('removes address at prefix from file path', () => {
    const rawStack = `Error: Test error
    at Component (address at index.bundle:123:45)
    at render (address at address at index.bundle:456:78)`;

    const result = formatRawStack(rawStack);

    expect(result).toBe(
      'at Component (index.bundle:123:45)\nat render (index.bundle:456:78)',
    );
  });

  test('extracts bundle name from full path', () => {
    const rawStack = `Error: Test error
    at Component (/path/to/index.bundle:123:45)
    at render (/another/path/to/index.bundle:456:78)`;

    const result = formatRawStack(rawStack);

    expect(result).toBe(
      'at Component (index.bundle:123:45)\nat render (index.bundle:456:78)',
    );
  });

  test('handles stack trace without file information', () => {
    const rawStack = `Error: Test error
    at Component
    at render`;

    const result = formatRawStack(rawStack);

    expect(result).toBe('at Component\nat render');
  });

  test('handles mixed stack trace with and without file information', () => {
    const rawStack = `Error: Test error
    at Component (index.bundle:123:45)
    at render
    at process (index.bundle:789:12)`;

    const result = formatRawStack(rawStack);

    expect(result).toBe(
      'at Component (index.bundle:123:45)\nat render\nat process (index.bundle:789:12)',
    );
  });

  test('filters out empty lines', () => {
    const rawStack = `Error: Test error

    at Component (index.bundle:123:45)

    at render (index.bundle:456:78)`;

    const result = formatRawStack(rawStack);

    expect(result).toBe(
      'at Component (index.bundle:123:45)\nat render (index.bundle:456:78)',
    );
  });

  test('filters out lines without "at " prefix', () => {
    const rawStack = `Error: Test error
    Some other line
    at Component (index.bundle:123:45)
    Another line without at
    at render (index.bundle:456:78)`;

    const result = formatRawStack(rawStack);

    expect(result).toBe(
      'at Component (index.bundle:123:45)\nat render (index.bundle:456:78)',
    );
  });

  test('handles empty stack trace', () => {
    const result = formatRawStack('');

    expect(result).toBe('');
  });

  test('handles stack trace with only whitespace', () => {
    const result = formatRawStack('   \n  \n   ');

    expect(result).toBe('');
  });

  test('preserves function names with special characters', () => {
    const rawStack = `Error: Test error
    at Component$render (index.bundle:123:45)
    at render$with$nested$calls (index.bundle:456:78)`;

    const result = formatRawStack(rawStack);

    expect(result).toBe(
      'at Component$render (index.bundle:123:45)\nat render$with$nested$calls (index.bundle:456:78)',
    );
  });

  test('handles React Native bundle paths', () => {
    const rawStack = `Error: Test error
    at Component (index.android.bundle:123:45)
    at render (index.ios.bundle:456:78)`;

    const result = formatRawStack(rawStack);

    expect(result).toBe(
      'at Component (index.android.bundle:123:45)\nat render (index.ios.bundle:456:78)',
    );
  });

  test('handles case-insensitive address at prefix', () => {
    const rawStack = `Error: Test error
    at Component (ADDRESS AT index.bundle:123:45)
    at render (Address At index.bundle:456:78)`;

    const result = formatRawStack(rawStack);

    expect(result).toBe(
      'at Component (index.bundle:123:45)\nat render (index.bundle:456:78)',
    );
  });
});

