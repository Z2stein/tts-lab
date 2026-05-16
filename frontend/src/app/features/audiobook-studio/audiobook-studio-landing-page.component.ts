import { CommonModule } from '@angular/common';
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
  constructor(private readonly router: Router) {}

  onProjectCreated(projectId: string): void {
    void this.router.navigate(['/audiobook-studio', projectId], {
      state: { scrollToSection: 'cast-section' }
    });
  }
}
