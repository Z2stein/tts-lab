import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  AudiobookLibraryDisplay,
  AudiobookProject,
  Character,
  SpeechSegment,
  AIGenerationRun,
  AudioAsset,
  RenderSegment,
} from '../models/audiobook-library.types';
import { AudiobookLibraryService } from '../services/audiobook-library.service';

@Component({
  selector: 'app-audiobook-library-display',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './audiobook-library-display.component.html',
  styleUrls: ['./audiobook-library-display.component.css'],
})
export class AudiobookLibraryDisplayComponent implements OnInit {
  @Input() projectId!: string;

  libraryDisplay: AudiobookLibraryDisplay | null = null;
  isLoading = true;
  error: string | null = null;

  constructor(private audiobookLibraryService: AudiobookLibraryService) {}

  ngOnInit(): void {
    this.loadLibraryDisplay();
  }

  private async loadLibraryDisplay(): Promise<void> {
    try {
      this.isLoading = true;
      this.error = null;
      this.libraryDisplay = await this.audiobookLibraryService.getLibraryDisplay(this.projectId);
    } catch (err) {
      this.error = err instanceof Error ? err.message : 'Failed to load audiobook library display';
      console.error('Error loading library display:', err);
    } finally {
      this.isLoading = false;
    }
  }

  get project(): AudiobookProject | null {
    return this.libraryDisplay?.project ?? null;
  }

  get characters(): Character[] {
    return this.libraryDisplay?.characters ?? [];
  }

  get segments(): SpeechSegment[] {
    return this.libraryDisplay?.segments ?? [];
  }

  get generationRuns(): AIGenerationRun[] {
    return this.libraryDisplay?.generationRuns ?? [];
  }

  get assets(): AudioAsset[] {
    return this.libraryDisplay?.assets ?? [];
  }

  getCharacterName(characterId: string): string {
    const character = this.characters.find((c) => c.id === characterId);
    return character?.name ?? 'Unknown Character';
  }

  getCharacterDescription(characterId: string): string | undefined {
    const character = this.characters.find((c) => c.id === characterId);
    return character?.roleDescription;
  }

  getCharacterVoice(characterId: string): string {
    const character = this.characters.find((c) => c.id === characterId);
    return character?.voiceKey ?? 'Default';
  }

  getSegmentCount(): number {
    return this.segments.length;
  }

  getCharacterCount(): number {
    return this.characters.length;
  }

  getTotalDuration(): number {
    return (
      this.assets.reduce((total, asset) => {
        return total + (asset.durationMs ? asset.durationMs / 1000 : 0);
      }, 0) || 0
    );
  }

  formatDuration(seconds: number): string {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = Math.floor(seconds % 60);

    if (hours > 0) {
      return `${hours}h ${minutes}m ${secs}s`;
    } else if (minutes > 0) {
      return `${minutes}m ${secs}s`;
    } else {
      return `${secs}s`;
    }
  }

  formatDate(dateString: string): string {
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  getRunStatusColor(status: string): string {
    switch (status) {
      case 'SUCCEEDED':
        return 'success';
      case 'RUNNING':
        return 'info';
      case 'PENDING':
        return 'warning';
      case 'FAILED':
        return 'danger';
      case 'STALE':
        return 'secondary';
      default:
        return 'light';
    }
  }

  getRenderSegmentsForRun(run: AIGenerationRun): RenderSegment[] {
    return run.renders ?? [];
  }

  getApprovedSegmentCount(): number {
    return this.segments.filter((s) => s.approved).length;
  }

  getApprovedCharacterCount(): number {
    return this.characters.filter((c) => c.approved).length;
  }
}
