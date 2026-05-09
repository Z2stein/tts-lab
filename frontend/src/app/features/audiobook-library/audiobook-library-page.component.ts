import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AudiobookCardComponent } from './components/audiobook-card.component';
import { EmptyLibraryStateComponent } from './components/empty-library-state.component';
import { AudiobookSummary } from './models/audiobook-library.types';
import { AudiobookLibraryService } from './services/audiobook-library.service';

@Component({
  selector: 'app-audiobook-library-page',
  standalone: true,
  imports: [CommonModule, RouterLink, AudiobookCardComponent, EmptyLibraryStateComponent],
  template: `
    <section class="relative -mx-4 -my-8 min-h-[calc(100vh-6rem)] overflow-hidden bg-studio-bg px-4 py-10 text-studio-text sm:-mx-8 sm:px-8" aria-labelledby="library-title">
      <div class="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_20%_10%,rgba(122,116,255,0.22),transparent_30%),radial-gradient(circle_at_85%_0%,rgba(240,173,93,0.16),transparent_28%)]"></div>
      <div class="relative mx-auto grid max-w-6xl gap-8">
        <header class="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p class="eyebrow">My Audiobooks</p>
            <h1 id="library-title" class="m-0 text-4xl font-black tracking-normal text-studio-text sm:text-5xl">Library</h1>
            <p class="mt-3 max-w-2xl text-studio-muted">Review generated previews, inspect scene readiness, and download ready MP3 assets.</p>
          </div>
          <a class="secondary-button" routerLink="/audiobook-studio">Open studio</a>
        </header>

        <p *ngIf="error" class="error" role="alert">{{ error }}</p>
        <section *ngIf="loading" class="app-panel" role="status">Loading audiobook library...</section>
        <app-empty-library-state *ngIf="!loading && !error && projects.length === 0"></app-empty-library-state>

        <div *ngIf="!loading && projects.length > 0" class="grid gap-5 lg:grid-cols-2">
          <app-audiobook-card *ngFor="let project of projects; trackBy: trackProject" [project]="project"></app-audiobook-card>
        </div>
      </div>
    </section>
  `
})
export class AudiobookLibraryPageComponent implements OnInit {
  projects: AudiobookSummary[] = [];
  loading = true;
  error: string | null = null;

  constructor(private readonly audiobookLibraryService: AudiobookLibraryService) {}

  async ngOnInit(): Promise<void> {
    try {
      this.projects = await this.audiobookLibraryService.list();
    } catch {
      this.error = 'The audiobook library could not be loaded. Please try again later.';
    } finally {
      this.loading = false;
    }
  }

  trackProject(_: number, project: AudiobookSummary): string {
    return project.id;
  }
}
