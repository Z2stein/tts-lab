import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { AudioAssetResponse, AudiobookSpeechSegmentResponse } from '../../../shared/api-contract.generated';
import { parsePerformanceDirections } from '../utils/performance-parser';

@Component({
  selector: 'app-audiobook-speech-segment-list',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './audiobook-speech-segment-list.component.html'
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
