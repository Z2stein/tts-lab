import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-ai-generation-overlay',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './ai-generation-overlay.component.html',
})
export class AiGenerationOverlayComponent {
  @Input() visible = false;
}
