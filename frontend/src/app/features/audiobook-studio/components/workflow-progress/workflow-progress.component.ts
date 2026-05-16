import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { WorkflowStep } from '../../models/audiobook-studio.types';

@Component({
  selector: 'app-workflow-progress',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './workflow-progress.component.html',
  styleUrl: './workflow-progress.component.css'
})
export class WorkflowProgressComponent {
  @Input() steps: WorkflowStep[] = [];
  @Output() scrollTo = new EventEmitter<string>();

  onStepClick(sectionId: string, event: Event): void {
    event.preventDefault();
    this.scrollTo.emit(sectionId);
  }
}
