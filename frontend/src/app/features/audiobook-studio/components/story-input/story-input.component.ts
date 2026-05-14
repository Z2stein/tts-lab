import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

@Component({
  selector: 'app-story-input',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './story-input.component.html',
  styleUrls: ['../audiobook-studio-section-shared.css', './story-input.component.css']
})
export class StoryInputComponent {
  @Input() storyControl!: FormControl<string>;
  @Input() loadingAction: string | null = null;
  @Output() useSampleStory = new EventEmitter<void>();
  @Output() analyzeStory = new EventEmitter<void>();

  get wordCount(): number {
    return this.storyControl.value.trim().split(/\s+/).filter(Boolean).length;
  }

  get characterCount(): number {
    return this.storyControl.value.length;
  }

  isLoading(action: string): boolean {
    return this.loadingAction === action;
  }
}
