import { CommonModule } from '@angular/common';
import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  NgZone,
  OnDestroy,
  Output,
  ViewChild
} from '@angular/core';
import { WORKFLOW_STEPPER_CONTENT } from '../../data/studio-content';
import { WorkflowStep, WorkflowStepKey } from '../../models/audiobook-studio.types';

type StepperVisualState = 'completed' | 'current' | 'upcoming';
export type StepperTier = 'wide' | 'compact';

/** Below this panel width the stepper switches to the compact (badges-only)
 *  tier and the detail surface becomes a bottom-sheet instead of a popover.
 *  Kept in sync with the `@container` breakpoint in styles.css. */
export const STEPPER_COMPACT_MAX_WIDTH = 560;

export interface StepperStepView {
  key: WorkflowStepKey;
  number: number;
  title: string;
  description: string;
  shortLabel: string;
  statusLabel: string;
  sectionId: string;
  visual: StepperVisualState;
  isCurrent: boolean;
}

/**
 * Single compact, sticky workflow orientation component. Replaces the old
 * journey-grid card section and the workflow progress bar. Density adapts to
 * the available width via CSS `@container` tiers; this component only tracks a
 * coarse `tier` (wide | compact) to decide popover vs. bottom-sheet.
 */
@Component({
  selector: 'app-workflow-stepper',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './workflow-stepper.component.html',
  host: { class: 'contents' }
})
export class WorkflowStepperComponent implements AfterViewInit, OnDestroy {
  @Input() steps: WorkflowStep[] = [];
  @Output() scrollTo = new EventEmitter<string>();

  @ViewChild('sentinel') private sentinelRef!: ElementRef<HTMLElement>;
  @ViewChild('panel') private panelRef!: ElementRef<HTMLElement>;

  isStuck = false;
  tier: StepperTier = 'wide';
  activeDetailKey: WorkflowStepKey | null = null;

  private sentinelObserver: IntersectionObserver | null = null;
  private resizeObserver: ResizeObserver | null = null;
  private closeTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(private readonly ngZone: NgZone) {}

  ngAfterViewInit(): void {
    this.sentinelObserver = new IntersectionObserver(
      ([entry]) => {
        this.ngZone.run(() => {
          this.isStuck = !entry.isIntersecting && entry.boundingClientRect.top < 0;
        });
      },
      { threshold: 0 }
    );
    this.sentinelObserver.observe(this.sentinelRef.nativeElement);

    this.resizeObserver = new ResizeObserver((entries) => {
      const width = entries[0]?.contentRect.width ?? 0;
      const next: StepperTier = width > 0 && width < STEPPER_COMPACT_MAX_WIDTH ? 'compact' : 'wide';
      if (next !== this.tier) {
        this.ngZone.run(() => {
          this.tier = next;
          this.activeDetailKey = null;
        });
      }
    });
    this.resizeObserver.observe(this.panelRef.nativeElement);
  }

  ngOnDestroy(): void {
    this.sentinelObserver?.disconnect();
    this.resizeObserver?.disconnect();
    this.clearCloseTimer();
  }

  get isCompact(): boolean {
    return this.tier === 'compact';
  }

  get views(): StepperStepView[] {
    return this.steps.map((step) => {
      const content = WORKFLOW_STEPPER_CONTENT[step.key];
      const visual = this.visualFor(step.status);
      return {
        key: step.key,
        number: content.number,
        title: content.title,
        description: content.description,
        shortLabel: step.label,
        statusLabel: step.statusLabel,
        sectionId: step.sectionId,
        visual,
        isCurrent: visual === 'current'
      };
    });
  }

  /** Title of the active/current step, shown beneath the bar in compact tier. */
  get currentStepTitle(): string {
    return this.views.find((view) => view.isCurrent)?.title ?? '';
  }

  get activeDetail(): StepperStepView | null {
    if (this.activeDetailKey === null) {
      return null;
    }
    return this.views.find((view) => view.key === this.activeDetailKey) ?? null;
  }

  private visualFor(status: WorkflowStep['status']): StepperVisualState {
    if (status === 'completed') {
      return 'completed';
    }
    if (status === 'current' || status === 'warning') {
      return 'current';
    }
    return 'upcoming';
  }

  toggleDetail(key: WorkflowStepKey): void {
    this.clearCloseTimer();
    this.activeDetailKey = this.activeDetailKey === key ? null : key;
  }

  /** Desktop hover-open: keep the detail pinned while the pointer is over the
   *  step or its popover. No-op in the compact tier (tap opens a sheet). */
  openOnHover(key: WorkflowStepKey): void {
    if (this.isCompact) {
      return;
    }
    this.clearCloseTimer();
    this.activeDetailKey = key;
  }

  /** Desktop hover-out: delay the close so the pointer can travel across the
   *  small gap into the popover (so "Jump to step" stays clickable). */
  scheduleClose(): void {
    if (this.isCompact) {
      return;
    }
    this.clearCloseTimer();
    this.closeTimer = setTimeout(() => {
      this.ngZone.run(() => {
        this.activeDetailKey = null;
        this.closeTimer = null;
      });
    }, 220);
  }

  cancelScheduledClose(): void {
    this.clearCloseTimer();
  }

  closeDetail(): void {
    this.clearCloseTimer();
    this.activeDetailKey = null;
  }

  private clearCloseTimer(): void {
    if (this.closeTimer !== null) {
      clearTimeout(this.closeTimer);
      this.closeTimer = null;
    }
  }

  jumpToStep(view: StepperStepView, event?: Event): void {
    event?.preventDefault();
    event?.stopPropagation();
    this.scrollTo.emit(view.sectionId);
    this.closeDetail();
  }

  onKeydown(event: KeyboardEvent, view: StepperStepView): void {
    if (event.key === 'Escape') {
      this.closeDetail();
      return;
    }
    if (event.key === 'Enter' || event.key === ' ' || event.key === 'Spacebar') {
      event.preventDefault();
      this.toggleDetail(view.key);
    }
  }

  trackByKey(_: number, view: StepperStepView): WorkflowStepKey {
    return view.key;
  }
}
