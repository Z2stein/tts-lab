import { Component, ElementRef, Input, OnDestroy, ViewChild } from '@angular/core';

@Component({
  selector: 'app-demo-audio-preview',
  standalone: true,
  templateUrl: './demo-audio-preview.component.html'
})
export class DemoAudioPreviewComponent implements OnDestroy {
  @Input({ required: true }) audioSrc = '';
  @Input() label = 'Demo preview';
  @Input() waveformBars: number[] = [];

  @ViewChild('audioElement')
  audioElement?: ElementRef<HTMLAudioElement>;

  isPlaying = false;
  currentTime = 0;
  duration = 0;
  audioError: string | null = null;

  get progressPercent(): number {
    if (!this.duration) {
      return 0;
    }

    return Math.min(100, Math.max(0, (this.currentTime / this.duration) * 100));
  }

  get formattedCurrentTime(): string {
    return this.formatTime(this.currentTime);
  }

  get formattedDuration(): string {
    return this.duration ? this.formatTime(this.duration) : '0:00';
  }

  async togglePlayback(): Promise<void> {
    const audio = this.audioElement?.nativeElement;
    if (!audio) {
      return;
    }

    this.audioError = null;

    if (audio.paused) {
      try {
        await audio.play();
        this.isPlaying = true;
      } catch {
        this.isPlaying = false;
        this.audioError = 'Audio preview could not be started.';
      }
      return;
    }

    audio.pause();
    this.isPlaying = false;
  }

  updateDuration(): void {
    const duration = this.audioElement?.nativeElement.duration ?? 0;
    this.duration = Number.isFinite(duration) ? duration : 0;
  }

  updateProgress(): void {
    const currentTime = this.audioElement?.nativeElement.currentTime ?? 0;
    this.currentTime = Number.isFinite(currentTime) ? currentTime : 0;
  }

  seek(event: Event): void {
    const audio = this.audioElement?.nativeElement;
    const input = event.target as HTMLInputElement | null;
    if (!audio || !input || !this.duration) {
      return;
    }

    const nextPercent = Number(input.value);
    if (!Number.isFinite(nextPercent)) {
      return;
    }

    audio.currentTime = (nextPercent / 100) * this.duration;
    this.updateProgress();
  }

  handleEnded(): void {
    this.isPlaying = false;
    this.updateProgress();
  }

  handlePause(): void {
    this.isPlaying = false;
  }

  handlePlay(): void {
    this.isPlaying = true;
  }

  handleAudioError(): void {
    this.isPlaying = false;
    this.audioError = 'Audio preview is currently unavailable.';
  }

  ngOnDestroy(): void {
    const audio = this.audioElement?.nativeElement;
    if (audio) {
      audio.pause();
    }
  }

  private formatTime(value: number): string {
    if (!Number.isFinite(value) || value < 0) {
      return '0:00';
    }

    const totalSeconds = Math.floor(value);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = (totalSeconds % 60).toString().padStart(2, '0');
    return `${minutes}:${seconds}`;
  }
}
