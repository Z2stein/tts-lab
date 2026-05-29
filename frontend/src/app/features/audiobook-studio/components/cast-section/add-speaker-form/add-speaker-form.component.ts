import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SpeakerVoiceAnalysisItem } from '../../../../audiobook-shared/service/audiobook-workflow.service';
import { normalizeVoiceAssetName } from '../../../utils/speaker-name';

@Component({
  selector: 'app-add-speaker-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './add-speaker-form.component.html',
})
export class AddSpeakerFormComponent {
  @Input() draft!: SpeakerVoiceAnalysisItem;
  @Output() save = new EventEmitter<void>();
  @Output() cancel = new EventEmitter<void>();
  @Output() pickVoice = new EventEmitter<void>();

  validationError: string | null = null;

  get hasVoice(): boolean {
    return !!normalizeVoiceAssetName(this.draft.voiceSuggestion);
  }

  onSave(): void {
    if (!this.draft.speakerName.trim()) {
      this.validationError = 'Speaker name is required.';
      return;
    }
    this.validationError = null;
    this.save.emit();
  }

  onCancel(): void {
    this.validationError = null;
    this.cancel.emit();
  }
}
