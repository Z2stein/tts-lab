import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-voice-search-input',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './voice-search-input.component.html',
})
export class VoiceSearchInputComponent {
  @Input() value = '';
  @Output() valueChange = new EventEmitter<string>();
}
