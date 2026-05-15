import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { IndexedSpeakerSplitTurn, SpeakerSplitTurn } from '../../../models/audiobook-studio.types';
import { formatSpeakerDisplayName } from '../../../utils/speaker-name';

@Component({
  selector: 'app-script-turn',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './script-turn.component.html',
  styleUrls: ['../../audiobook-studio-section-shared.css', './script-turn.component.css']
})
export class ScriptTurnComponent {
  @Input() indexedTurn!: IndexedSpeakerSplitTurn;
  @Input() editing = false;
  @Input() editDraft: SpeakerSplitTurn | null = null;
  @Input() speakerOptions: string[] = [];
  @Input() loadingAction: string | null = null;
  @Output() startEdit = new EventEmitter<void>();
  @Output() saveEdit = new EventEmitter<void>();
  @Output() cancelEdit = new EventEmitter<void>();

  displayName(name: string): string {
    return formatSpeakerDisplayName(name);
  }

  trackSpeakerOption(_: number, name: string): string {
    return name;
  }

  isLoading(): boolean {
    return this.loadingAction !== null;
  }
}
