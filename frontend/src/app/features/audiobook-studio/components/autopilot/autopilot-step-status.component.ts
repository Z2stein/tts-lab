import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { AutopilotStep, AutopilotStepId } from '../../services/autopilot.service';

@Component({
  selector: 'app-autopilot-step-status',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './autopilot-step-status.component.html',
  host: { class: 'block' }
})
export class AutopilotStepStatusComponent {
  @Input() step!: AutopilotStep;
  @Input() position = 0;
  @Input() progressMessage: string | null = null;
  @Output() open = new EventEmitter<AutopilotStepId>();

  onOpen(): void {
    this.open.emit(this.step.id);
  }
}
