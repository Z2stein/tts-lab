import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, signal } from '@angular/core';
import { FormControl } from '@angular/forms';
import { IndexedSpeakerSplitTurn, ScriptGroup, SpeakerSplitTurn } from '../../models/audiobook-studio.types';
import { formatSpeakerDisplayName } from '../../utils/speaker-name';
import { AiHintPanelComponent } from '../ai-hint-panel/ai-hint-panel.component';
import { ScriptTurnComponent } from './script-turn/script-turn.component';

@Component({
  selector: 'app-script-review',
  standalone: true,
  imports: [CommonModule, AiHintPanelComponent, ScriptTurnComponent],
  templateUrl: './script-review.component.html'
})
export class ScriptReviewComponent implements OnChanges {
  @Input() emotionAnnotationHintControl!: FormControl<string>;
  @Input() scriptTurns: SpeakerSplitTurn[] = [];
  @Input() scriptGroups: ScriptGroup[] = [];
  @Input() scriptApproved = false;
  @Input() isCompleted = false;
  @Input() editingScriptTurnIndex: number | null = null;
  @Input() scriptTurnEditDraft: SpeakerSplitTurn | null = null;
  @Input() speakerOptions: string[] = [];
  @Input() loadingAction: string | null = null;
  @Input() speakerStyleFn!: (name: string | null | undefined) => Record<string, string>;
  @Output() startTurnEdit = new EventEmitter<number>();
  @Output() saveTurnEdit = new EventEmitter<number>();
  @Output() cancelTurnEdit = new EventEmitter<void>();
  @Output() approveScript = new EventEmitter<void>();

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

  displayName(name: string): string {
    return formatSpeakerDisplayName(name);
  }

  trackScriptGroup(_: number, group: ScriptGroup): string {
    return `${group.speaker}:${group.turns[0]?.index ?? 0}`;
  }

  trackScriptTurn(_: number, indexedTurn: IndexedSpeakerSplitTurn): number {
    return indexedTurn.index;
  }

  isLoading(action: string): boolean {
    return this.loadingAction === action;
  }

  hasOpenEdit(): boolean {
    return this.editingScriptTurnIndex !== null;
  }
}
