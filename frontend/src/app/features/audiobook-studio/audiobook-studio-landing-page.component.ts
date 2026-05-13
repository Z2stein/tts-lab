import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AudiobookStudioWorkspaceComponent } from './audiobook-studio-page.component';

@Component({
  selector: 'app-audiobook-studio-landing-page',
  standalone: true,
  imports: [CommonModule, AudiobookStudioWorkspaceComponent],
  template: `
    <app-audiobook-studio-workspace
      [showHero]="true"
      (projectCreated)="onProjectCreated($event)"
    ></app-audiobook-studio-workspace>
  `
})
export class AudiobookStudioLandingPageComponent {
  constructor(private readonly router: Router) {}

  onProjectCreated(projectId: string): void {
    void this.router.navigate(['/audiobook-studio', projectId]);
  }
}
