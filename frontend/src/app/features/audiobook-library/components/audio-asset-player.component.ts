import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { AudioAssetResponse } from '../../../shared/api-contract.generated';

@Component({
  selector: 'app-audio-asset-player',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './audio-asset-player.component.html'
})
export class AudioAssetPlayerComponent {
  @Input({ required: true }) asset!: AudioAssetResponse;
  @Input() testId = 'audio-asset-player';
  readonly bars = [28, 62, 44, 78, 35, 92, 58, 70, 42, 82, 48, 64, 30, 74, 52, 88, 40, 68];

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
