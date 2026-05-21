import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, signal } from '@angular/core';
import { FormControl } from '@angular/forms';
import { SpeakerVoiceAnalysisItem } from '../../../audiobook-shared/service/audiobook-workflow.service';
import { AiHintPanelComponent } from '../ai-hint-panel/ai-hint-panel.component';
import { AddSpeakerFormComponent } from './add-speaker-form/add-speaker-form.component';
import { CastCardComponent } from './cast-card/cast-card.component';

@Component({
  selector: 'app-cast-section',
  standalone: true,
  imports: [CommonModule, AiHintPanelComponent, AddSpeakerFormComponent, CastCardComponent],
  templateUrl: './cast-section.component.html'
})
export class CastSectionComponent implements OnChanges {
  @Input() cast: SpeakerVoiceAnalysisItem[] = [];
  @Input() castReviewed = false;
  @Input() isCompleted = false;
  @Input() speakerSplitHintControl!: FormControl<string>;
  @Input() editingCastIndex: number | null = null;
  @Input() castEditDraft: SpeakerVoiceAnalysisItem | null = null;
  @Input() castEditable = true;
  @Input() loadingAction: string | null = null;
  @Input() addingSpeaker = false;
  @Input() addSpeakerDraft: SpeakerVoiceAnalysisItem | null = null;
  @Input() speakerStyleFn!: (name: string | null | undefined) => Record<string, string>;
  @Output() startCastEdit = new EventEmitter<number>();
  @Output() saveCastEdit = new EventEmitter<number>();
  @Output() cancelCastEdit = new EventEmitter<void>();
  @Output() createScriptPreview = new EventEmitter<void>();
  @Output() changeVoice = new EventEmitter<number>();
  @Output() removeSpeaker = new EventEmitter<number>();
  @Output() startAddSpeaker = new EventEmitter<void>();
  @Output() saveAddSpeaker = new EventEmitter<void>();
  @Output() cancelAddSpeaker = new EventEmitter<void>();
  @Output() pickVoiceForNewSpeaker = new EventEmitter<void>();

  readonly collapsed = signal(false);
  private _hasAutoCollapsed = false;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['isCompleted']?.currentValue === true && !this._hasAutoCollapsed) {
      this._hasAutoCollapsed = true;
      this.collapsed.set(true);
    }
  }

  toggleCollapsed(): void {
    this.collapsed.update(v => !v);
  }

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
