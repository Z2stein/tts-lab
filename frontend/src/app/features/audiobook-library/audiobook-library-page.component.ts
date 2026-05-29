import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { AudiobookCardComponent } from './components/audiobook-card.component';
import { AudioPlayerModalComponent } from './components/audio-player-modal.component';
import { EmptyLibraryStateComponent } from './components/empty-library-state.component';
import { AudiobookSummary, AudioAssetResponse } from '../../shared/api-contract.generated';
import { AudiobookLibraryService } from './services/audiobook-library.service';
import { PageRevisitService } from '../../shared/services/page-revisit.service';

@Component({
  selector: 'app-audiobook-library-page',
  standalone: true,
  imports: [CommonModule, RouterLink, AudiobookCardComponent, AudioPlayerModalComponent, EmptyLibraryStateComponent],
  templateUrl: './audiobook-library-page.component.html'
})
export class AudiobookLibraryPageComponent implements OnInit, OnDestroy {
  projects: AudiobookSummary[] = [];
  loading = true;
  error: string | null = null;
  selectedAsset: AudioAssetResponse | null = null;

  private revisitSubscription?: Subscription;

  constructor(
    private readonly audiobookLibraryService: AudiobookLibraryService,
    private readonly pageRevisitService: PageRevisitService
  ) {}

  async ngOnInit(): Promise<void> {
    await this.loadInitial();
    this.revisitSubscription = this.pageRevisitService.revisits$.subscribe(() => {
      void this.refresh();
    });
  }

  ngOnDestroy(): void {
    this.revisitSubscription?.unsubscribe();
  }

  trackProject(_: number, project: AudiobookSummary): string {
    return project.id;
  }

  onPlayPreview(asset: AudioAssetResponse): void {
    this.selectedAsset = asset;
  }

  onCloseModal(): void {
    this.selectedAsset = null;
  }

  private async loadInitial(): Promise<void> {
    try {
      this.projects = await this.audiobookLibraryService.list();
    } catch {
      this.error = 'The audiobook library could not be loaded. Please try again later.';
    } finally {
      this.loading = false;
    }
  }

  /**
   * Silently re-fetches the library when the user returns to an already-open
   * page, so deletions made elsewhere are reflected without a hard reload.
   * Keeps the currently shown list on failure rather than flashing an error.
   */
  private async refresh(): Promise<void> {
    try {
      this.projects = await this.audiobookLibraryService.list();
      this.error = null;
    } catch (error) {
      console.error('Background refresh of the audiobook library failed.', error);
    }
  }
}
