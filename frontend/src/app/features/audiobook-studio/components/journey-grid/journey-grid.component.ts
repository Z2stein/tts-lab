import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
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

  onStepClick(sectionId: string, event: Event): void {
    event.preventDefault();
    this.scrollTo.emit(sectionId);
  }
}
