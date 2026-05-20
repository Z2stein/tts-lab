import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { StoryGeneratorComponent } from '../story-input/story-generator/story-generator.component';

const MIN_STORY_CHARACTERS = 20;

@Component({
  selector: 'app-autopilot-setup-card',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, StoryGeneratorComponent],
  templateUrl: './autopilot-setup-card.component.html',
  host: { class: 'block' }
})
export class AutopilotSetupCardComponent {
  @Input() storyControl!: FormControl<string>;
  @Input() running = false;
  @Output() start = new EventEmitter<void>();

  readonly activeTab = signal<'paste' | 'generate'>('paste');

  get characterCount(): number {
    return this.storyControl?.value.trim().length ?? 0;
  }

  get canStart(): boolean {
    return !this.running && this.characterCount >= MIN_STORY_CHARACTERS;
  }

  onStoryGenerated(text: string): void {
    this.storyControl.setValue(text);
    this.activeTab.set('paste');
  }

  onStart(): void {
    if (this.canStart) {
      this.start.emit();
    }
  }
}
