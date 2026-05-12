import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { IndexedSpeakerSplitTurn, ScriptGroup, SpeakerSplitTurn } from '../../models/audiobook-studio.types';
import { formatSpeakerDisplayName } from '../../utils/speaker-name';
import { ScriptTurnComponent } from './script-turn/script-turn.component';

@Component({
  selector: 'app-script-review',
  standalone: true,
  imports: [CommonModule, ScriptTurnComponent],
  templateUrl: './script-review.component.html',
  styleUrl: './script-review.component.css'
})
export class ScriptReviewComponent {
  @Input() scriptTurns: SpeakerSplitTurn[] = [];
  @Input() scriptGroups: ScriptGroup[] = [];
  @Input() scriptApproved = false;
  @Input() editingScriptTurnIndex: number | null = null;
  @Input() scriptTurnEditDraft: SpeakerSplitTurn | null = null;
  @Input() speakerOptions: string[] = [];
  @Input() loadingAction: string | null = null;
  @Input() speakerStyleFn!: (name: string | null | undefined) => Record<string, string>;
  @Output() startTurnEdit = new EventEmitter<number>();
  @Output() saveTurnEdit = new EventEmitter<number>();
  @Output() cancelTurnEdit = new EventEmitter<void>();
  @Output() approveScript = new EventEmitter<void>();
  @Output() addEmotionAndPacing = new EventEmitter<void>();

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
