import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
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
export class PerformanceNotesComponent {
  @Input() annotatedTurns: AnnotatedSpeakerTurn[] = [];
  @Input() performanceNotesStale = false;
  @Input() performanceReady = false;
  @Input() promptControl!: FormControl<string>;
  @Input() languageCodeControl!: FormControl<string>;
  @Input() modelNameControl!: FormControl<string>;
  @Input() audioEncodingControl!: FormControl<string>;
  @Input() loadingAction: string | null = null;
  @Input() languageCodeOptions: readonly { label: string; value: string }[] = [];
  @Input() modelNameOptions: readonly string[] = [];
  @Input() speakerStyleFn!: (name: string | null | undefined) => Record<string, string>;
  @Output() createAudioProductionPlan = new EventEmitter<void>();

  markupFor(turn: AnnotatedSpeakerTurn): AnnotatedMarkup {
    return markupFor(turn);
  }

  displayName(name: string): string {
    return formatSpeakerDisplayName(name);
  }

  isLoading(action: string): boolean {
    return this.loadingAction === action;
  }
}
