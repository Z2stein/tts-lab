import { CommonModule } from '@angular/common';
import { AfterViewInit, Component, ElementRef, EventEmitter, Input, NgZone, OnDestroy, Output, ViewChild } from '@angular/core';
import { WorkflowStep } from '../../models/audiobook-studio.types';

@Component({
  selector: 'app-workflow-progress',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './workflow-progress.component.html',
  host: { class: 'contents' }
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

  get isMobileSticky(): boolean {
    return this.isSticky && this.isNarrow;
  }

  get displayMode(): 'flex' | 'grid' {
    // Option 1: Sticky + small screen => flexbox layout
    return this.isMobileSticky ? 'flex' : 'grid';
  }

  get currentStepIndex(): number {
    return this.steps.findIndex(s => s.status === 'current' || s.status === 'warning');
  }

  get visibleSteps(): WorkflowStep[] {
    return this.steps;
  }

  getGridColumns(): string {
    // Option 2: Nonsticky + small screen => 1 column (all 5 stacked)
    if (this.isNarrow && !this.isSticky) {
      return 'repeat(1, minmax(0, 1fr))';
    }
    // Option 3 & 4: Regular screen (sticky or nonsticky) => 5 columns
    return 'repeat(5, minmax(0, 1fr))';
  }

  onStepClick(sectionId: string, event: Event): void {
    event.preventDefault();
    this.scrollTo.emit(sectionId);
  }
}
