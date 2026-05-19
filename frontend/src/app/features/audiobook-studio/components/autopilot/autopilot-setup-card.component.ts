import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

const MIN_STORY_CHARACTERS = 20;

@Component({
  selector: 'app-autopilot-setup-card',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './autopilot-setup-card.component.html',
  host: { class: 'block' }
})
export class AutopilotSetupCardComponent {
  @Input() storyControl!: FormControl<string>;
  @Input() running = false;
  @Output() start = new EventEmitter<void>();

  get characterCount(): number {
    return this.storyControl?.value.trim().length ?? 0;
  }

  get canStart(): boolean {
    return !this.running && this.characterCount >= MIN_STORY_CHARACTERS;
  }

  onStart(): void {
    if (this.canStart) {
      this.start.emit();
    }
  }
}
