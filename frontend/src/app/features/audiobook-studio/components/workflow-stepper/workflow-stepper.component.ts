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
 * journey-grid card section and the workflow progress bar.
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

  isStuck = false;
  isMobile = false;
  activeDetailKey: WorkflowStepKey | null = null;

  private sentinelObserver: IntersectionObserver | null = null;
  private mobileMq: MediaQueryList | null = null;
  private readonly mobileMqHandler = (e: MediaQueryListEvent): void => {
    this.ngZone.run(() => {
      this.isMobile = e.matches;
      this.activeDetailKey = null;
    });
  };

  constructor(private readonly ngZone: NgZone) {}

  ngAfterViewInit(): void {
    this.mobileMq = window.matchMedia('(max-width: 640px)');
    this.isMobile = this.mobileMq.matches;
    this.mobileMq.addEventListener('change', this.mobileMqHandler);

    this.sentinelObserver = new IntersectionObserver(
      ([entry]) => {
        this.ngZone.run(() => {
          this.isStuck = !entry.isIntersecting && entry.boundingClientRect.top < 0;
        });
      },
      { threshold: 0 }
    );
    this.sentinelObserver.observe(this.sentinelRef.nativeElement);
  }

  ngOnDestroy(): void {
    this.sentinelObserver?.disconnect();
    this.mobileMq?.removeEventListener('change', this.mobileMqHandler);
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
    this.activeDetailKey = this.activeDetailKey === key ? null : key;
  }

  hoverDetail(key: WorkflowStepKey): void {
    if (!this.isMobile) {
      this.activeDetailKey = key;
    }
  }

  hoverLeave(key: WorkflowStepKey): void {
    if (!this.isMobile && this.activeDetailKey === key) {
      this.activeDetailKey = null;
    }
  }

  closeDetail(): void {
    this.activeDetailKey = null;
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
