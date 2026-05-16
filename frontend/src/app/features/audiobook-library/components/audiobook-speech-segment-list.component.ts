import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { AudioAssetResponse, AudiobookSpeechSegmentResponse } from '../../../shared/api-contract.generated';
import { parsePerformanceDirections } from '../utils/performance-parser';

@Component({
  selector: 'app-audiobook-speech-segment-list',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './audiobook-speech-segment-list.component.css',
  template: `
    <section class="rounded-lg border border-studio-line bg-studio-panel/85 p-5 backdrop-blur" data-testid="speech-segment-list">
      <div class="mb-4">
        <p class="eyebrow">Speech segment details</p>
        <h2 class="m-0 text-2xl font-black">Performance notes</h2>
      </div>

      <ng-container *ngIf="speechSegments && speechSegments.length > 0">
        <div class="speech-segment-list">
          <article *ngFor="let speechSegment of speechSegments; let i = index" class="speech-segment-item" data-testid="speech-segment-row" [style.--speaker-accent]="getSpeakerColor(speechSegment.speakerName)">
            <div class="speech-segment-header">
              <span class="speech-segment-number">{{ i + 1 }}.</span>
              <span class="speech-segment-title">{{ speechSegment.title }}</span>
            </div>

            <div class="speech-segment-content">
              <div class="speaker-row">
                <span class="speaker-name">{{ speechSegment.speakerName }}</span>
                <span *ngIf="speechSegment.speakerRoleDescription" class="speaker-role">{{ speechSegment.speakerRoleDescription }}</span>
              </div>

              <div class="emotion-tags">
                <span *ngFor="let tag of getEmotionTags(speechSegment)" class="emotion-badge">{{ tag }}</span>
              </div>

              <div *ngIf="getStyledText(speechSegment)" class="speech-segment-text">
                {{ getStyledText(speechSegment) }}
              </div>

              <div *ngIf="speechSegment.voiceName" class="speech-segment-meta">
                <span class="voice-label">Voice:</span> {{ speechSegment.voiceName }}
              </div>
            </div>
          </article>
        </div>
      </ng-container>
      <ng-container *ngIf="!speechSegments || speechSegments.length === 0">
        <div class="empty-state">No speech segments to display</div>
      </ng-container>
    </section>
  `
})
export class AudiobookSpeechSegmentListComponent {
  @Input() set speechSegments(value: AudiobookSpeechSegmentResponse[] | undefined | null) {
    this._speechSegments = value || [];
  }
  get speechSegments(): AudiobookSpeechSegmentResponse[] {
    return this._speechSegments;
  }
  private _speechSegments: AudiobookSpeechSegmentResponse[] = [];

  @Input() audioAssets: AudioAssetResponse[] = [];

  private readonly speakerColors = [
    '#FF6B6B', '#4ECDC4', '#45B7D1',
    '#FFA07A', '#98D8C8', '#F7DC6F'
  ];

  readyAssets(speechSegmentId: string): AudioAssetResponse[] {
    return this.audioAssets.filter((asset) => asset.speechSegmentId === speechSegmentId && asset.status === 'READY');
  }

  durationLabel(seconds: number | null): string {
    if (seconds === null) return 'Duration pending';
    const minutes = Math.floor(seconds / 60);
    const remaining = seconds % 60;
    return `${minutes}:${remaining.toString().padStart(2, '0')}`;
  }

  getEmotionTags(speechSegment: AudiobookSpeechSegmentResponse): string[] {
    const parsed = parsePerformanceDirections(speechSegment.performanceDirections);
    return parsed.emotionTags;
  }

  getOriginalText(speechSegment: AudiobookSpeechSegmentResponse): string | undefined {
    const parsed = parsePerformanceDirections(speechSegment.performanceDirections);
    return parsed.originalText;
  }

  getSpeakerColor(speakerName?: string | null): string {
    if (!speakerName) return this.speakerColors[0];
    const hash = speakerName.charCodeAt(0);
    return this.speakerColors[hash % this.speakerColors.length];
  }

  getStyledText(speechSegment: AudiobookSpeechSegmentResponse): string | undefined {
    return this.getOriginalText(speechSegment);
  }
}
