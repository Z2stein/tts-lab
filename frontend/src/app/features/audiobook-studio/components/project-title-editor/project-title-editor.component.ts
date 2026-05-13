import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

@Component({
  selector: 'app-project-title-editor',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './project-title-editor.component.html'
})
export class ProjectTitleEditorComponent {
  @Input() projectTitleControl!: FormControl<string>;
  @Input() projectTitleEditing = false;
  @Input() projectTitleVisible = false;
  @Input() loadingAction: string | null = null;
  @Output() startProjectTitleEdit = new EventEmitter<void>();
  @Output() saveProjectTitle = new EventEmitter<void>();
  @Output() cancelProjectTitleEdit = new EventEmitter<void>();

  isLoading(action: string): boolean {
    return this.loadingAction === action;
  }
}
