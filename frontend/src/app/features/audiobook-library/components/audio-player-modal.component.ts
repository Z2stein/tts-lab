import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, ViewChild, ElementRef } from '@angular/core';
import { WaveSurferService } from '../../audiobook-studio/services/wave-surfer.service';
import { AudioAssetResponse } from '../../../shared/api-contract.generated';

@Component({
  selector: 'app-audio-player-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './audio-player-modal.component.html'
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
