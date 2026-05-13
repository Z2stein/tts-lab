import { CommonModule } from '@angular/common';
import { Component, Input, Output, EventEmitter } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AudiobookSummary, AudioAssetResponse } from '../../../shared/api-contract.generated';

@Component({
  selector: 'app-audiobook-card',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <article class="grid gap-5 rounded-lg border border-studio-line bg-studio-panel/85 p-5 shadow-[0_24px_80px_rgba(0,0,0,0.3)] backdrop-blur" data-testid="audiobook-card">
      <div class="flex items-start justify-between gap-4">
        <div>
          <span class="badge">{{ statusLabel(project.status) }}</span>
          <h2 class="mb-1 mt-3 text-2xl font-black text-studio-text">{{ project.title }}</h2>
          <p class="m-0 text-sm text-studio-muted">Updated {{ updatedLabel(project.updatedAt) }}</p>
        </div>
        <div class="hidden h-16 w-20 items-end gap-1 rounded-md border border-studio-line bg-[#0b0910] p-2 sm:flex" aria-hidden="true">
          <span *ngFor="let bar of bars" class="w-full rounded-t bg-gradient-to-t from-studio-accent to-[#7a74ff]" [style.height.%]="bar"></span>
        </div>
      </div>

      <dl class="grid grid-cols-3 gap-2 text-sm">
        <div class="rounded-md border border-studio-line bg-studio-field p-3">
          <dt class="text-studio-muted">Speech segments</dt>
          <dd class="m-0 text-lg font-extrabold">{{ project.speechSegmentCount }}</dd>
        </div>
        <div class="rounded-md border border-studio-line bg-studio-field p-3">
          <dt class="text-studio-muted">Speakers</dt>
          <dd class="m-0 text-lg font-extrabold">{{ project.speakerCount ?? 'TBD' }}</dd>
        </div>
        <div class="rounded-md border border-studio-line bg-studio-field p-3">
          <dt class="text-studio-muted">Duration</dt>
          <dd class="m-0 text-lg font-extrabold">{{ durationLabel(project.totalDurationSeconds) }}</dd>
        </div>
      </dl>

      <div class="flex flex-wrap gap-2">
        <a class="primary-button" [routerLink]="['/audiobook-library', project.id]" data-testid="continue-review">Continue review</a>
        <button *ngIf="readyAsset" class="secondary-button" (click)="onPlayPreview()" data-testid="play-preview">Play preview</button>
        <a *ngIf="readyAsset" class="secondary-button" [href]="readyAsset.downloadUrl" [download]="readyAsset.filename" data-testid="download-asset">Download</a>
      </div>
    </article>
  `
})
export class AudiobookCardComponent {
  @Input({ required: true }) project!: AudiobookSummary;
  @Output() playPreview = new EventEmitter<AudioAssetResponse>();
  readonly bars = [30, 65, 45, 86, 58, 72, 38, 90];

  get readyAsset() {
    return this.project.audioAssets.find((asset) => asset.status === 'READY') ?? null;
  }

  onPlayPreview(): void {
    if (this.readyAsset) {
      this.playPreview.emit(this.readyAsset);
    }
  }

  statusLabel(status: string): string {
    return status.replace('_', ' ');
  }

  durationLabel(seconds: number | null): string {
    if (seconds === null) return 'TBD';
    const minutes = Math.floor(seconds / 60);
    const remaining = seconds % 60;
    return `${minutes}:${remaining.toString().padStart(2, '0')}`;
  }

  updatedLabel(value: string): string {
    return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
  }
}
