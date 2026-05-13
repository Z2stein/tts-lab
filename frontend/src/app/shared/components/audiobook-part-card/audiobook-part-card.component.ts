import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { WaveformPlayerComponent } from '../../../features/audiobook-studio/components/waveform-player/waveform-player.component';
import { VoiceSampleService } from '../../../features/audiobook-studio/services/voice-sample.service';
import { LoggerService } from '../../../logger.service';

export interface AudiobookPartCard {
  partNumber: number;
  totalParts: number;
  speakerName?: string | null;
  speakerRole?: string | null;
  voiceName?: string | null;
  emotionTags?: string[];
  originalText?: string | null;
  durationSeconds?: number | null;
  status: 'PENDING' | 'NEEDS_CHANGES' | 'APPROVED';
  audioUrl?: string | null;
  readyAssetId?: string | null;
  error?: string;
}

@Component({
  selector: 'app-audiobook-part-card',
  standalone: true,
  imports: [CommonModule, WaveformPlayerComponent],
  providers: [VoiceSampleService],
  templateUrl: './audiobook-part-card.component.html',
  styleUrl: './audiobook-part-card.component.css',
})
export class AudiobookPartCardComponent {
  @Input({ required: true }) part!: AudiobookPartCard;
  @Input({ required: true }) partNumber!: number;
  @Input({ required: true }) totalParts!: number;
  @Input() readonly = true;

  playing = false;

  constructor(private readonly logger: LoggerService) {}

  isDirectionTag(tag: string): boolean {
    const directionPatterns = [
      'short pause',
      'long pause',
      'breath',
      'sigh',
      'laugh',
      'gasp',
      'grunt',
      'moan',
      'sob',
      'whisper',
      'yell',
      'shout',
    ];
    return directionPatterns.some((pattern) => tag.toLowerCase().includes(pattern));
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'PENDING':
        return 'Ready to listen';
      case 'NEEDS_CHANGES':
        return 'Needs changes';
      case 'APPROVED':
        return 'Approved';
      default:
        return status;
    }
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'PENDING':
        return 'ready';
      case 'APPROVED':
        return 'approved';
      case 'NEEDS_CHANGES':
        return 'failed';
      default:
        return '';
    }
  }

  getSpeakerAccentClass(partNumber: number): string {
    return `cast-accent-${partNumber % 6}`;
  }

  downloadAsset(assetId: string): void {
    // Placeholder - to be implemented by parent component or service
    this.logger.info('audiobook-part-card', 'Download asset', { assetId });
  }

  durationLabel(seconds: number | null | undefined): string {
    if (seconds === null || seconds === undefined) return '';
    const minutes = Math.floor(seconds / 60);
    const remaining = seconds % 60;
    return `${minutes}:${remaining.toString().padStart(2, '0')}`;
  }
}
