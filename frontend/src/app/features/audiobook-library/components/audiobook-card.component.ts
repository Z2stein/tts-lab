import { CommonModule } from '@angular/common';
import { Component, Input, Output, EventEmitter } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AudiobookSummary, AudioAssetResponse } from '../../../shared/api-contract.generated';
import { buildPreviewFilename, selectProjectPreviewAsset } from './audiobook-card.helpers';

@Component({
  selector: 'app-audiobook-card',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './audiobook-card.component.html'
})
export class AudiobookCardComponent {
  private _project!: AudiobookSummary;
  @Output() playPreview = new EventEmitter<AudioAssetResponse>();
  readonly bars = [30, 65, 45, 86, 58, 72, 38, 90];
  previewAsset: AudioAssetResponse | null = null;

  @Input({ required: true })
  set project(value: AudiobookSummary) {
    this._project = value;
    this.previewAsset = selectProjectPreviewAsset(value.audioAssets);
  }

  get project(): AudiobookSummary {
    return this._project;
  }

  onPlayPreview(): void {
    if (this.previewAsset) {
      this.playPreview.emit(this.previewAsset);
    }
  }

  onDownloadPreview(): void {
    if (!this.previewAsset) {
      return;
    }

    const a = document.createElement('a');
    a.href = this.previewAsset.downloadUrl;
    a.download = buildPreviewFilename(this.project.title);
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
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
