import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { JourneyStep } from '../../models/audiobook-studio.types';

@Component({
  selector: 'app-journey-grid',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './journey-grid.component.html',
  styleUrl: './journey-grid.component.css'
})
export class JourneyGridComponent {
  @Input() steps: readonly JourneyStep[] = [];
  @Output() scrollTo = new EventEmitter<string>();

  private readonly iconSvgs: Record<string, string> = {
    '📝': '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" preserveAspectRatio="xMidYMid meet"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path></svg>',
    '🎤': '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" preserveAspectRatio="xMidYMid meet"><path d="M12 2a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z"></path><path d="M19 10v2a7 7 0 0 1-14 0v-2"></path><line x1="12" y1="19" x2="12" y2="23"></line><line x1="8" y1="23" x2="16" y2="23"></line></svg>',
    '✓': '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" preserveAspectRatio="xMidYMid meet"><polyline points="20 7 9 18 4 13"></polyline></svg>',
    '🎵': '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" preserveAspectRatio="xMidYMid meet"><path d="M9 18V5l12-2v13"></path><circle cx="6" cy="18" r="3"></circle><circle cx="18" cy="16" r="3"></circle></svg>',
    '▶': '<svg viewBox="0 0 24 24" fill="currentColor" preserveAspectRatio="xMidYMid meet"><polygon points="6 4 20 12 6 20"></polygon></svg>'
  };

  constructor(private sanitizer: DomSanitizer) {}

  getStepIcon(stepIcon: string): SafeHtml {
    const svg = this.iconSvgs[stepIcon] || this.iconSvgs['▶'];
    return this.sanitizer.bypassSecurityTrustHtml(svg);
  }

  onStepClick(sectionId: string, event: Event): void {
    event.preventDefault();
    this.scrollTo.emit(sectionId);
  }
}
