import { Injectable } from '@angular/core';

export type LogLevel = 'info' | 'warn' | 'error';

@Injectable({ providedIn: 'root' })
export class LoggerService {
  info(prefix: string, message: string, data?: unknown): void {
    this.log('info', prefix, message, data);
  }

  warn(prefix: string, message: string, data?: unknown): void {
    this.log('warn', prefix, message, data);
  }

  error(prefix: string, message: string, data?: unknown): void {
    this.log('error', prefix, message, data);
  }

  private log(level: LogLevel, prefix: string, message: string, data?: unknown): void {
    const formattedMessage = `[${prefix}] ${message}`;

    if (level === 'error') {
      console.error(formattedMessage, data);
    } else {
      console.warn(formattedMessage, data);
    }
  }
}
