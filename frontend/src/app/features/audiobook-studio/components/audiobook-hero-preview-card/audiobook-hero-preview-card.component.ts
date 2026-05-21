import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HeroCastMember } from '../../models/audiobook-studio.types';
import { SPEAKER_ACCENTS } from '../../data/studio-content';

@Component({
  selector: 'app-audiobook-hero-preview-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './audiobook-hero-preview-card.component.html',
})
export class AudiobookHeroPreviewCardComponent {
  @Input() cast: readonly HeroCastMember[] = [];
  @Input() storyExcerpt = 'The rain tapped against the window...';
  @Input() duration = '00:24';
  @Input() playing = false;
  /** Playback progress 0..1 used to fill the waveform bars. */
  @Input() progress = 0;

  @Output() togglePlay = new EventEmitter<Event>();

  // Static heights (px) for the decorative waveform — not real audio data.
  readonly waveformBars: readonly number[] = [
    7, 13, 9, 18, 11, 22, 14, 8, 20, 12, 26, 10, 16, 23, 9, 14, 19, 7,
    24, 12, 17, 10, 21, 14, 8, 27, 13, 18, 9, 15, 22, 11, 7, 19, 13, 25,
    10, 16, 8, 20, 12, 23, 9, 14, 18, 7, 21, 11,
  ];

  accentColor(index: number): string {
    return SPEAKER_ACCENTS[index % SPEAKER_ACCENTS.length].color;
  }

  barActive(index: number): boolean {
    return index < Math.round(this.progress * this.waveformBars.length);
  }
}
