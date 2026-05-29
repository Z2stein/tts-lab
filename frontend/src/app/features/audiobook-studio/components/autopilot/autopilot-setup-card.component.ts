import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { startWith } from 'rxjs/operators';
import { StoryGeneratorComponent } from '../story-input/story-generator/story-generator.component';

const MIN_STORY_CHARACTERS = 20;
// Delay before the "create from a rough idea" hint fades in on an empty story,
// so it never flashes while the user is already typing.
const IDEA_CTA_DELAY_MS = 1000;

@Component({
  selector: 'app-autopilot-setup-card',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, StoryGeneratorComponent],
  templateUrl: './autopilot-setup-card.component.html',
  host: { class: 'block' }
})
export class AutopilotSetupCardComponent implements OnInit, OnDestroy {
  @Input() storyControl!: FormControl<string>;
  @Input() running = false;
  @Output() start = new EventEmitter<void>();

  readonly activeTab = signal<'paste' | 'generate'>('paste');
  readonly showIdeaCta = signal(false);

  private storySub?: Subscription;
  private ctaTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    this.storySub = this.storyControl.valueChanges
      .pipe(startWith(this.storyControl.value))
      .subscribe((value) => this.refreshIdeaCta(value));
  }

  ngOnDestroy(): void {
    this.storySub?.unsubscribe();
    this.clearCtaTimer();
  }

  // Hide the hint as soon as the story has custom text; otherwise reveal it
  // after a short delay so it only appears once the field has settled empty.
  private refreshIdeaCta(value: string): void {
    if ((value ?? '').trim().length > 0) {
      this.clearCtaTimer();
      this.showIdeaCta.set(false);
      return;
    }
    if (this.showIdeaCta() || this.ctaTimer !== null) {
      return;
    }
    this.ctaTimer = setTimeout(() => {
      this.ctaTimer = null;
      this.showIdeaCta.set(true);
    }, IDEA_CTA_DELAY_MS);
  }

  private clearCtaTimer(): void {
    if (this.ctaTimer !== null) {
      clearTimeout(this.ctaTimer);
      this.ctaTimer = null;
    }
  }

  openIdeaTab(): void {
    this.activeTab.set('generate');
  }

  get wordCount(): number {
    return this.storyControl?.value.trim().split(/\s+/).filter(Boolean).length ?? 0;
  }

  get characterCount(): number {
    return this.storyControl?.value.length ?? 0;
  }

  get canStart(): boolean {
    return !this.running && (this.storyControl?.value.trim().length ?? 0) >= MIN_STORY_CHARACTERS;
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
