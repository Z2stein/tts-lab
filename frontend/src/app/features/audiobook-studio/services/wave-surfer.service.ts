import { Injectable } from '@angular/core';
import WaveSurfer from 'wavesurfer.js';

export const WAVEFORM_VISUAL_OPTIONS = {
  height: 58,
  waveColor: '#596174',
  progressColor: '#f0ad5d',
  cursorColor: '#ffd591',
  cursorWidth: 2,
  barWidth: 3,
  barGap: 3,
  barRadius: 3,
  normalize: true,
  dragToSeek: true,
} as const;

@Injectable({ providedIn: 'root' })
export class WaveSurferService {
  private instances = new Map<string, WaveSurfer>();

  create(key: string, container: HTMLElement, url: string): WaveSurfer {
    container.innerHTML = '';
    const ws = WaveSurfer.create({ ...WAVEFORM_VISUAL_OPTIONS, container, url });
    this.instances.set(key, ws);
    return ws;
  }

  get(key: string): WaveSurfer | null {
    return this.instances.get(key) ?? null;
  }

  destroy(key: string): void {
    this.instances.get(key)?.destroy();
    this.instances.delete(key);
  }

  destroyAll(): void {
    this.instances.forEach((ws) => ws.destroy());
    this.instances.clear();
  }

  pauseAll(except?: WaveSurfer): void {
    this.instances.forEach((ws) => {
      if (ws !== except && ws.isPlaying()) {
        ws.pause();
      }
    });
  }

  bind(
    waveSurfer: WaveSurfer,
    onPlay: () => void,
    onStateChange: (playing: boolean) => void
  ): void {
    waveSurfer.on('play', () => {
      onPlay();
      onStateChange(true);
    });
    waveSurfer.on('pause', () => onStateChange(false));
    waveSurfer.on('finish', () => onStateChange(false));
  }
}
