import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ChatbotService } from '../../../../../chatbot/chatbot.service';

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

  readonly chips = ['Add narrator', 'Add 2 characters', 'Make it dramatic', 'Suitable for voice acting'] as const;
  readonly selectedChips = signal(new Set<string>());
  readonly generating = signal(false);
  readonly error = signal<string | null>(null);

  get charCount(): number {
    return this.ideaControl.value.length;
  }

  get canGenerate(): boolean {
    return this.ideaControl.value.trim().length > 0 && !this.generating();
  }

  constructor(private readonly chatbotService: ChatbotService) {}

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
      const response = await this.chatbotService.sendMessage(this.buildPrompt(idea), null);
      this.storyGenerated.emit(response.answer);
    } catch (err) {
      this.error.set(err instanceof Error ? err.message : 'Failed to generate story. Please try again.');
    } finally {
      this.generating.set(false);
    }
  }

  private buildPrompt(idea: string): string {
    const selected = this.selectedChips();
    const lines = [
      `Write a short story suitable for audiobook production based on this idea: ${idea}`,
      '',
      'Requirements:',
      '- Format each line with a speaker label and a colon (e.g. "Narrator: ...", "Elena: ...")',
      '- Keep it between 250 and 500 words',
    ];

    if (selected.has('Add narrator')) lines.push('- Include a narrator character');
    if (selected.has('Add 2 characters')) lines.push('- Include 2 or more named characters');
    if (selected.has('Make it dramatic')) lines.push('- Use a dramatic, suspenseful tone');
    if (selected.has('Suitable for voice acting')) lines.push('- Write clear, distinct dialogue that sounds natural when spoken aloud');

    lines.push('', 'Output only the story text with no preamble or explanation.');
    return lines.join('\n');
  }
}
