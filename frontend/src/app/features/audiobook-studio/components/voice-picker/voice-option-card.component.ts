import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SpeakerVoiceCatalogItem } from '../../../../shared/api-contract.generated';

@Component({
  selector: 'app-voice-option-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './voice-option-card.component.html',
})
export class VoiceOptionCardComponent {
  @Input() voice!: SpeakerVoiceCatalogItem;
  @Input() selected = false;
  @Input() demoPlaying = false;
  @Output() selectVoice = new EventEmitter<SpeakerVoiceCatalogItem>();
  @Output() toggleDemo = new EventEmitter<SpeakerVoiceCatalogItem>();

  onImageError(event: Event): void {
    const img = event.target as HTMLImageElement;
    img.style.display = 'none';
  }
}
