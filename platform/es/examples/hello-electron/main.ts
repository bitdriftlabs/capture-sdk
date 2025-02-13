import { app, BrowserWindow } from 'electron';
import {
  init,
  debug,
  generateDeviceCode,
  getDeviceID,
  SessionStrategy,
} from '@bitdrift/electron';
import { config as configDotenv } from 'dotenv';

configDotenv();

// Initialize bitdrift
init(process.env['BITDRIFT_KEY'] ?? 'invalid key', SessionStrategy.Fixed, {
  url: process.env['BITDRIFT_URL'],
  appVersion: '1.0.0',
  autoAddMainListener: {
    experimental: {
      sessionReplayEnabled: true,
    },
  },
});

const createWindow = async () => {
  const win = new BrowserWindow({
    width: 800,
    height: 600,
    webPreferences: {
      contextIsolation: true,
      preload: __dirname + '/preload.cjs',
    },
  });

  debug('App started');
  win.loadFile('index.html');
  console.log(`Device Code: ${await generateDeviceCode()}`);
  console.log(`Device ID: ${getDeviceID()}`);
  win.webContents.openDevTools();
};

app.whenReady().then(() => {
  createWindow();
});
