import { contextBridge, ipcRenderer } from 'electron';

export type Fields = Record<string, string | number | boolean | Buffer>;

const factory = (level: number) => (msg: string, fields: Fields) =>
  ipcRenderer.send('bitdrift:log', level, msg, fields);

contextBridge.exposeInMainWorld('logger', {
  trace: factory(0),
  debug: factory(1),
  info: factory(2),
  warn: factory(3),
  error: factory(4),
});
