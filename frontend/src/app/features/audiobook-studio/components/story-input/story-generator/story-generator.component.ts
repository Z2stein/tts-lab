import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { AudiobookWorkflowService } from '../../../../../features/audiobook-shared/service/audiobook-workflow.service';

@Component({
  selector: 'app-story-generator',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './story-generator.component.html'
})
export class StoryGeneratorComponent {
  @Input() disabled = false;
  @Output() readonly storyGenerated = new EventEmitter<string>();

  readonly ideaControl = new FormControl('', { nonNullable: true });

  readonly chips = ['Add narrator', 'Add 2 characters', '3+ characters', 'Make it dramatic', 'Make it poetic', 'Make it emotional', 'Suitable for voice acting'] as const;
  readonly selectedChips = signal(new Set<string>());
  readonly generating = signal(false);
  readonly error = signal<string | null>(null);

  get charCount(): number {
    return this.ideaControl.value.length;
  }

  get canGenerate(): boolean {
    return this.ideaControl.value.trim().length > 0 && !this.generating();
  }

  constructor(private readonly workflowService: AudiobookWorkflowService) {}

  isChipSelected(chip: string): boolean {
    return this.selectedChips().has(chip);
  }

  toggleChip(chip: string): void {
    const next = new Set(this.selectedChips());
    if (next.has(chip)) {
      next.delete(chip);
    } else {
      next.add(chip);
    }
    this.selectedChips.set(next);
  }

  async generateStory(): Promise<void> {
    const idea = this.ideaControl.value.trim();
    if (!idea) return;

    this.generating.set(true);
    this.error.set(null);

    try {
      const response = await this.workflowService.generateStoryDraft({
        idea,
        enhancements: Array.from(this.selectedChips())
      });
      this.storyGenerated.emit(response.storyDraft);
    } catch (err) {
      this.error.set(err instanceof Error ? err.message : 'Failed to generate story. Please try again.');
    } finally {
      this.generating.set(false);
    }
  }
}
