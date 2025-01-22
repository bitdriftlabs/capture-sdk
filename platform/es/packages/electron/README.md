# @bitdrift/electron

## Overview

This is the package containing the Electron support for bitdrift. See https://docs.bitdrift.io for more information.

## Installation

```bash
npm install @bitdrift/electron
```

## Usage

```javascript
import { init, info, SessionStrategy, getSessionID, generateDeviceID } from '@bitdrift/electron';

// Initialize the bitdrift SDK. This is expected to be called from the main process.
init('<api-key>', SessionStrategy.Activity);

// Send a custom log message at info level. Other log levels are trace, debug, warn, and error.
// This is expected to be called from the main process.
info('Hello, world!');

// Get the current session ID. See https://docs.bitdrift.io/sdk/features#session-management for more information about session management.
const sessionID = getSessionID();

// Generate a new device ID that can be used with the `devicecode` operator in bd tail. See https://docs.bitdrift.io/cli/quickstart.html#log-tailing for more information.
const deviceID = generateDeviceID();
```
