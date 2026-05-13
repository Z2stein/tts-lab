import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, ViewChild, ElementRef } from '@angular/core';
import { WaveSurferService } from '../../audiobook-studio/services/wave-surfer.service';
import { AudioAssetResponse } from '../../../shared/api-contract.generated';

@Component({
  selector: 'app-audio-player-modal',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/50" (click)="onBackdropClick()">
      <div class="relative w-full max-w-xl rounded-lg border border-studio-line bg-studio-panel shadow-[0_24px_80px_rgba(0,0,0,0.3)] p-6" (click)="$event.stopPropagation()">
        <button class="absolute right-4 top-4 text-studio-muted hover:text-studio-text transition-colors" (click)="onClose()" aria-label="Close player">
          <svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
          </svg>
        </button>

        <div class="mb-6">
          <p class="eyebrow">{{ asset.type.replace('_', ' ') }} · v{{ asset.version }}</p>
          <h2 class="m-0 mt-2 text-2xl font-black text-studio-text">{{ asset.filename }}</h2>
          <p class="m-0 mt-1 text-sm text-studio-muted">{{ fileSizeLabel(asset.sizeBytes) }} · {{ durationLabel(asset.durationSeconds) }}</p>
        </div>

        <div #waveformContainer class="mb-6 rounded-md border border-studio-line bg-[#0b0910] p-3"></div>

        <div class="flex flex-wrap gap-3">
          <button class="primary-button flex-1" (click)="togglePlayPause()">
            {{ playing ? 'Pause' : 'Play' }}
          </button>
          <button class="secondary-button flex-1" (click)="onDownload()">
            Download
          </button>
          <button class="secondary-button flex-1" (click)="onClose()">
            Close
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    :host {
      ::ng-deep .audio-player-modal-wave {
        height: 120px;
      }
    }
  `]
})
export class AudioPlayerModalComponent implements OnInit, OnDestroy {
  @Input({ required: true }) asset!: AudioAssetResponse;
  @Output() close = new EventEmitter<void>();

  @ViewChild('waveformContainer') waveformContainer!: ElementRef;

  playing = false;
  private waveSurferId = '';

  constructor(private readonly waveSurferService: WaveSurferService) {
    this.waveSurferId = `audio-player-modal-${Math.random().toString(36).substring(2, 9)}`;
  }

  ngOnInit(): void {
    setTimeout(() => {
      if (this.waveformContainer?.nativeElement) {
        const waveSurfer = this.waveSurferService.create(
          this.waveSurferId,
          this.waveformContainer.nativeElement as HTMLElement,
          this.asset.streamUrl
        );

        this.waveSurferService.bind(
          waveSurfer,
          () => {
            this.playing = true;
          },
          (isPlaying: boolean) => {
            this.playing = isPlaying;
          }
        );
      }
    });
  }

  ngOnDestroy(): void {
    this.waveSurferService.destroy(this.waveSurferId);
  }

  togglePlayPause(): void {
    const waveSurfer = this.waveSurferService.get(this.waveSurferId);
    if (waveSurfer) {
      void waveSurfer.playPause();
    }
  }

  onClose(): void {
    this.close.emit();
  }

  onBackdropClick(): void {
    this.onClose();
  }

  onDownload(): void {
    const a = document.createElement('a');
    a.href = this.asset.downloadUrl;
    a.download = this.asset.filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  }

  durationLabel(seconds: number | null): string {
    if (seconds === null) return 'Duration pending';
    const minutes = Math.floor(seconds / 60);
    const remaining = seconds % 60;
    return `${minutes}:${remaining.toString().padStart(2, '0')}`;
  }

  fileSizeLabel(bytes: number): string {
    if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  }
}
