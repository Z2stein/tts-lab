import { Component, Input } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

@Component({
  selector: 'app-ai-hint-panel',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './ai-hint-panel.component.html'
})
export class AiHintPanelComponent {
  @Input() control!: FormControl<string>;
  @Input() placeholder = '';
}
