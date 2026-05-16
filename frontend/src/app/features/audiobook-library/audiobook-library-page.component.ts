import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AudiobookCardComponent } from './components/audiobook-card.component';
import { AudioPlayerModalComponent } from './components/audio-player-modal.component';
import { EmptyLibraryStateComponent } from './components/empty-library-state.component';
import { AudiobookSummary, AudioAssetResponse } from '../../shared/api-contract.generated';
import { AudiobookLibraryService } from './services/audiobook-library.service';

@Component({
  selector: 'app-audiobook-library-page',
  standalone: true,
  imports: [CommonModule, RouterLink, AudiobookCardComponent, AudioPlayerModalComponent, EmptyLibraryStateComponent],
  templateUrl: './audiobook-library-page.component.html'
})
export class AudiobookLibraryPageComponent implements OnInit {
  projects: AudiobookSummary[] = [];
  loading = true;
  error: string | null = null;
  selectedAsset: AudioAssetResponse | null = null;

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

  onPlayPreview(asset: AudioAssetResponse): void {
    this.selectedAsset = asset;
  }

  onCloseModal(): void {
    this.selectedAsset = null;
  }
}
