import { Injectable } from '@angular/core';

@Injectable()
export class VoiceSampleService {
  activeSampleKey: string | null = null;
  private audio: HTMLAudioElement | null = null;

  play(path: string, key: string, onPlayError?: () => void): void {
    if (this.activeSampleKey === key && this.audio && !this.audio.paused) {
      this.audio.pause();
      this.activeSampleKey = null;
      return;
    }

    this.audio?.pause();
    this.audio = new Audio(path);
    this.activeSampleKey = key;

    this.audio.addEventListener('pause', () => {
      if (this.audio?.paused) {
        this.activeSampleKey = null;
      }
    });
    this.audio.addEventListener('ended', () => {
      this.activeSampleKey = null;
    });
    this.audio.play().catch(() => {
      this.activeSampleKey = null;
      onPlayError?.();
    });
  }

  pause(): void {
    this.audio?.pause();
  }

  destroy(): void {
    this.audio?.pause();
    this.audio = null;
    this.activeSampleKey = null;
  }
}
