import { Fields } from './preload';

export interface IBitdriftAPI {
  debug: (msg: string, fields?: Fields) => void;
  info: (msg: string, fields?: Fields) => void;
  warn: (msg: string, fields?: Fields) => void;
  error: (msg: string, fields?: Fields) => void;
  trace: (msg: string, fields?: Fields) => void;
}

declare global {
  interface Window {
    logger: IBitdriftAPI;
  }
}
