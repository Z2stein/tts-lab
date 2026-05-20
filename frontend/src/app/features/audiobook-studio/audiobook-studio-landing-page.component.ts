import { CommonModule, Location } from '@angular/common';
import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AudiobookStudioWorkspaceComponent } from './audiobook-studio-page.component';

@Component({
  selector: 'app-audiobook-studio-landing-page',
  standalone: true,
  imports: [CommonModule, AudiobookStudioWorkspaceComponent],
  templateUrl: './audiobook-studio-landing-page.component.html'
})
export class AudiobookStudioLandingPageComponent {
  constructor(
    private readonly router: Router,
    private readonly location: Location,
  ) {}

  onProjectCreated(projectId: string): void {
    void this.router.navigate(['/audiobook-studio', projectId], {
      state: { scrollToSection: 'cast-section' }
    });
  }

  // Autopilot stays on this page while it runs; just reflect the audiobook URL
  // so the link is shareable and a reload resumes the project.
  onAutopilotProjectCreated(projectId: string): void {
    this.location.replaceState(`/audiobook-studio/${projectId}`);
  }
}
