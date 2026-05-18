import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { AiHintPanelComponent } from '../ai-hint-panel/ai-hint-panel.component';
import { StoryGeneratorComponent } from './story-generator/story-generator.component';

@Component({
  selector: 'app-story-input',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, AiHintPanelComponent, StoryGeneratorComponent],
  templateUrl: './story-input.component.html'
})
export class StoryInputComponent implements OnChanges {
  @Input() storyControl!: FormControl<string>;
  @Input() speakerAnalysisHintControl!: FormControl<string>;
  @Input() loadingAction: string | null = null;
  @Input() isCompleted = false;
  @Output() useSampleStory = new EventEmitter<void>();
  @Output() analyzeStory = new EventEmitter<void>();

  readonly activeTab = signal<'paste' | 'generate'>('generate');
  readonly collapsed = signal(false);
  private _hasAutoCollapsed = false;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['isCompleted']?.currentValue === true && !this._hasAutoCollapsed) {
      this._hasAutoCollapsed = true;
      this.collapsed.set(true);
    }
  }

  toggleCollapsed(): void {
    this.collapsed.update(v => !v);
  }

  get wordCount(): number {
    return this.storyControl.value.trim().split(/\s+/).filter(Boolean).length;
  }

  get characterCount(): number {
    return this.storyControl.value.length;
  }

  isLoading(action: string): boolean {
    return this.loadingAction === action;
  }

  onStoryGenerated(text: string): void {
    this.storyControl.setValue(text);
    this.activeTab.set('paste');
  }
}
