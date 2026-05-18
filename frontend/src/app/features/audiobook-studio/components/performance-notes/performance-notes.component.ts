import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { AnnotatedMarkup, AnnotatedSpeakerTurn } from '../../models/audiobook-studio.types';
import { markupFor } from '../../utils/annotated-markup';
import { formatSpeakerDisplayName } from '../../utils/speaker-name';
import { HighlightTagsPipe } from '../../pipes/highlight-tags.pipe';

@Component({
  selector: 'app-performance-notes',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, HighlightTagsPipe],
  templateUrl: './performance-notes.component.html'
})
export class PerformanceNotesComponent implements OnChanges {
  @Input() annotatedTurns: AnnotatedSpeakerTurn[] = [];
  @Input() performanceNotesStale = false;
  @Input() performanceReady = false;
  @Input() isCompleted = false;
  @Input() promptControl!: FormControl<string>;
  @Input() languageCodeControl!: FormControl<string>;
  @Input() sourceLanguageCode: string | null = null;
  @Input() modelNameControl!: FormControl<string>;
  @Input() audioEncodingControl!: FormControl<string>;
  @Input() loadingAction: string | null = null;
  @Input() languageCodeOptions: readonly { label: string; value: string }[] = [];
  @Input() modelNameOptions: readonly string[] = [];
  @Input() speakerStyleFn!: (name: string | null | undefined) => Record<string, string>;
  @Output() createAudioProductionPlan = new EventEmitter<void>();

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

  markupFor(turn: AnnotatedSpeakerTurn): AnnotatedMarkup {
    return markupFor(turn);
  }

  displayName(name: string): string {
    return formatSpeakerDisplayName(name);
  }

  isLoading(action: string): boolean {
    return this.loadingAction === action;
  }

  displayLanguageLabel(languageCode: string | null | undefined): string {
    if (!languageCode) {
      return 'Unknown';
    }
    return this.languageCodeOptions.find((option) => option.value === languageCode)?.label ?? languageCode;
  }
}
