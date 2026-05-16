import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { SpeakerVoiceAnalysisItem } from '../../../audiobook-shared/service/audiobook-workflow.service';
import { CastCardComponent } from './cast-card/cast-card.component';

@Component({
  selector: 'app-cast-section',
  standalone: true,
  imports: [CommonModule, CastCardComponent],
  templateUrl: './cast-section.component.html'
})
export class CastSectionComponent {
  @Input() cast: SpeakerVoiceAnalysisItem[] = [];
  @Input() castReviewed = false;
  @Input() editingCastIndex: number | null = null;
  @Input() castEditDraft: SpeakerVoiceAnalysisItem | null = null;
  @Input() loadingAction: string | null = null;
  @Input() speakerStyleFn!: (name: string | null | undefined) => Record<string, string>;
  @Output() startCastEdit = new EventEmitter<number>();
  @Output() saveCastEdit = new EventEmitter<number>();
  @Output() cancelCastEdit = new EventEmitter<void>();
  @Output() createScriptPreview = new EventEmitter<void>();
  @Output() changeVoice = new EventEmitter<number>();

  accentClass(index: number): string {
    return `cast-accent-${index % 6}`;
  }

  trackCastByIndex(index: number): number {
    return index;
  }

  isLoading(action: string): boolean {
    return this.loadingAction === action;
  }
}
