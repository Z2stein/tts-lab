import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { WaveformPlayerComponent } from '../../../audiobook-studio/components/waveform-player/waveform-player.component';
import { AudioAssetResponse } from '../../../../shared/api-contract.generated';

@Component({
  selector: 'app-complete-audiobook-player',
  standalone: true,
  imports: [CommonModule, WaveformPlayerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './complete-audiobook-player.component.html',
  styleUrl: './complete-audiobook-player.component.css',
})
export class CompleteAudiobookPlayerComponent {
  @Input({ required: true }) asset!: AudioAssetResponse;

  formatFileSize(bytes: number): string {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
  }

  formatDuration(seconds?: number | null): string {
    if (!seconds || seconds <= 0) return '0:00';
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60).toString().padStart(2, '0');
    return `${mins}:${secs}`;
  }
}
