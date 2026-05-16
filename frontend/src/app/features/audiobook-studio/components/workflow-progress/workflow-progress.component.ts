import { CommonModule } from '@angular/common';
import { AfterViewInit, Component, ElementRef, EventEmitter, Input, NgZone, OnDestroy, Output, ViewChild } from '@angular/core';
import { WorkflowStep } from '../../models/audiobook-studio.types';

@Component({
  selector: 'app-workflow-progress',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './workflow-progress.component.html',
  styleUrl: './workflow-progress.component.css'
})
export class WorkflowProgressComponent implements AfterViewInit, OnDestroy {
  @Input() steps: WorkflowStep[] = [];
  @Output() scrollTo = new EventEmitter<string>();

  @ViewChild('sentinel') private sentinelRef!: ElementRef<HTMLElement>;

  isSticky = false;
  isNarrow = false;

  private sentinelObserver: IntersectionObserver | null = null;
  private narrowMq: MediaQueryList | null = null;
  private readonly narrowMqHandler = (e: MediaQueryListEvent): void => {
    this.ngZone.run(() => { this.isNarrow = e.matches; });
  };

  constructor(private readonly ngZone: NgZone) {}

  ngAfterViewInit(): void {
    this.narrowMq = window.matchMedia('(max-width: 600px)');
    this.isNarrow = this.narrowMq.matches;
    this.narrowMq.addEventListener('change', this.narrowMqHandler);

    this.sentinelObserver = new IntersectionObserver(
      ([entry]) => {
        this.ngZone.run(() => {
          this.isSticky = !entry.isIntersecting && entry.boundingClientRect.top < 0;
        });
      },
      { threshold: 0 }
    );
    this.sentinelObserver.observe(this.sentinelRef.nativeElement);
  }

  ngOnDestroy(): void {
    this.sentinelObserver?.disconnect();
    this.narrowMq?.removeEventListener('change', this.narrowMqHandler);
  }

  get visibleSteps(): WorkflowStep[] {
    if (!this.isSticky || !this.isNarrow) return this.steps;
    const currentIdx = this.steps.findIndex(s => s.status === 'current' || s.status === 'warning');
    if (currentIdx === -1) return this.steps.slice(0, 2);
    const result: WorkflowStep[] = [this.steps[currentIdx]];
    if (currentIdx + 1 < this.steps.length) result.push(this.steps[currentIdx + 1]);
    return result;
  }

  onStepClick(sectionId: string, event: Event): void {
    event.preventDefault();
    this.scrollTo.emit(sectionId);
  }
}
