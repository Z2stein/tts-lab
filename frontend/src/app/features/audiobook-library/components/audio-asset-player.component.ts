import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { AudioAsset } from '../models/audiobook-library.types';

@Component({
  selector: 'app-audio-asset-player',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section *ngIf="asset" class="rounded-lg border border-studio-line bg-studio-field/80 p-4 shadow-[0_18px_60px_rgba(9,8,12,0.35)]" [attr.data-testid]="testId">
      <div class="mb-3 flex flex-wrap items-center justify-between gap-3">
        <div>
          <p class="eyebrow">{{ asset.type.replace('_', ' ') }} · v{{ asset.version }}</p>
          <h3 class="m-0 text-lg font-extrabold text-studio-text">{{ asset.filename }}</h3>
          <p class="m-0 mt-1 text-sm text-studio-muted">{{ fileSizeLabel(asset.sizeBytes) }} · {{ durationLabel(asset.durationSeconds) }}</p>
        </div>
        <a
          *ngIf="asset.status === 'READY'"
          class="secondary-button"
          [href]="asset.downloadUrl"
          [download]="asset.filename"
          data-testid="audio-download"
        >Download</a>
      </div>

      <div class="mb-3 flex h-16 items-end gap-1 overflow-hidden rounded-md border border-studio-line bg-[#0b0910] px-3 py-2" aria-hidden="true">
        <span *ngFor="let bar of bars; let i = index" class="w-full rounded-t bg-gradient-to-t from-studio-accent to-[#9c7cff]" [style.height.%]="bar"></span>
      </div>

      <audio *ngIf="asset.status === 'READY'" class="w-full" controls [src]="asset.streamUrl" data-testid="audio-player"></audio>
      <p *ngIf="asset.status !== 'READY'" class="empty-state m-0">Audio asset is {{ asset.status.toLowerCase() }}.</p>
    </section>
  `
})
export class AudioAssetPlayerComponent {
  @Input({ required: true }) asset!: AudioAsset;
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
