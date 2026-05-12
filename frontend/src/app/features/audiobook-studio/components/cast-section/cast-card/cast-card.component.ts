import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SpeakerVoiceAnalysisItem } from '../../../../audiobook-shared/service/audiobook-workflow.service';
import { formatSpeakerDisplayName, speakerInitials } from '../../../utils/speaker-name';

@Component({
  selector: 'app-cast-card',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './cast-card.component.html',
  styleUrl: './cast-card.component.css'
})
export class CastCardComponent {
  @Input() speaker!: SpeakerVoiceAnalysisItem;
  @Input() index!: number;
  @Input() accentClass = '';
  @Input() editing = false;
  @Input() editDraft: SpeakerVoiceAnalysisItem | null = null;
  @Input() isVoiceSamplePlaying = false;
  @Input() speakerStyleFn!: (name: string | null | undefined) => Record<string, string>;
  @Output() startEdit = new EventEmitter<void>();
  @Output() saveEdit = new EventEmitter<void>();
  @Output() cancelEdit = new EventEmitter<void>();
  @Output() playVoiceSample = new EventEmitter<void>();

  displayName(name: string): string {
    return formatSpeakerDisplayName(name);
  }

  initials(name: string): string {
    return speakerInitials(name);
  }
}
