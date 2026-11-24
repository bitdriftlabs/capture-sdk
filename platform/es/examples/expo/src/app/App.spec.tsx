import * as React from 'react';
import { render } from '@testing-library/react-native';

import App from './App';

test('renders correctly', () => {
  const { getByTestId } = render(<App />);
  expect(getByTestId('sdk-status')).toBeTruthy();
  expect(getByTestId('sdk-initialization-state')).toBeTruthy();
});
