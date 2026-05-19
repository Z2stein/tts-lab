import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { AutopilotStep } from '../../services/autopilot.service';

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
}
