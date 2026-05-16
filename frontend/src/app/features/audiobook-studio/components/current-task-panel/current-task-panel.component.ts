import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CurrentTask } from '../../models/audiobook-studio.types';

@Component({
  selector: 'app-workflow-current-task',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './current-task-panel.component.html',
  host: { class: 'block' }
})
export class CurrentTaskPanelComponent {
  @Input() currentTask!: CurrentTask;
  @Output() scrollTo = new EventEmitter<string>();

  onTaskClick(event: Event): void {
    event.preventDefault();
    this.scrollTo.emit(this.currentTask.sectionId);
  }
}
