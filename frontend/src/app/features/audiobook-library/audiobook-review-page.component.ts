import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AudioAssetPlayerComponent } from './components/audio-asset-player.component';
import { AudiobookSceneListComponent } from './components/audiobook-scene-list.component';
import { AudiobookDetail, AudioAsset, AudiobookScene } from './models/audiobook-library.types';
import { AudiobookLibraryService } from './services/audiobook-library.service';
import { AudiobookPartCardComponent } from '../../shared/components/audiobook-part-card/audiobook-part-card.component';
import { AudiobookPartCard } from '../../shared/components/audiobook-part-card/audiobook-part-card.component';

@Component({
  selector: 'app-audiobook-review-page',
  standalone: true,
  imports: [CommonModule, RouterLink, AudioAssetPlayerComponent, AudiobookSceneListComponent, AudiobookPartCardComponent],
  styleUrl: './audiobook-review-page.component.css',
  template: `
    <section class="relative -mx-4 -my-8 min-h-[calc(100vh-6rem)] overflow-hidden bg-studio-bg px-4 py-10 text-studio-text sm:-mx-8 sm:px-8" aria-labelledby="review-title">
      <div class="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_15%_0%,rgba(240,173,93,0.18),transparent_30%),radial-gradient(circle_at_80%_12%,rgba(122,116,255,0.22),transparent_30%)]"></div>
      <div class="relative mx-auto grid max-w-6xl gap-6">
        <a class="w-fit text-sm font-extrabold text-studio-accent no-underline" routerLink="/audiobook-library">Back to library</a>

        <p *ngIf="error" class="error" role="alert">{{ error }}</p>
        <section *ngIf="loading" class="app-panel" role="status">Loading audiobook review...</section>

        <ng-container *ngIf="!loading && detail">
          <header class="rounded-lg border border-studio-line bg-studio-panel/85 p-6 shadow-[0_24px_80px_rgba(0,0,0,0.3)] backdrop-blur">
            <p class="eyebrow">Review session</p>
            <div class="flex flex-wrap items-start justify-between gap-5">
              <div>
                <h1 id="review-title" class="m-0 text-4xl font-black sm:text-5xl">{{ detail.title }}</h1>
                <p class="mt-3 text-studio-muted">{{ detail.status.replace('_', ' ') }} · {{ detail.sceneCount }} scenes · Updated {{ updatedLabel(detail.updatedAt) }}</p>
              </div>
              <span class="badge">{{ readyAssets.length }} ready audio asset{{ readyAssets.length === 1 ? '' : 's' }}</span>
            </div>
          </header>

          <!-- Primary Asset Player -->
          <app-audio-asset-player *ngIf="primaryAsset" [asset]="primaryAsset" testId="primary-audio-player"></app-audio-asset-player>

          <!-- Audio Parts Section - Styled to match studio page -->
          <details class="audio-parts-details" open>
            <summary>
              <h2 class="m-0 text-2xl font-black">Audio parts and individual previews</h2>
            </summary>
            <div class="render-list">
              <app-audiobook-part-card
                *ngFor="let part of audioPartCards"
                [part]="part"
                [partNumber]="part.partNumber"
                [totalParts]="part.totalParts"
                [readonly]="true">
              </app-audiobook-part-card>
            </div>
          </details>

          <!-- Scene List -->
          <app-audiobook-scene-list [scenes]="detail.scenes" [audioAssets]="detail.audioAssets"></app-audiobook-scene-list>

          <!-- Available Files Section -->
          <section class="rounded-lg border border-studio-line bg-studio-panel/85 p-5 backdrop-blur" data-testid="audio-assets">
            <div class="mb-4">
              <p class="eyebrow">Available files</p>
              <h2 class="m-0 text-2xl font-black">Audio assets</h2>
            </div>
            <div class="grid gap-4">
              <app-audio-asset-player *ngFor="let asset of detail.audioAssets; trackBy: trackAsset" [asset]="asset"></app-audio-asset-player>
            </div>
          </section>
        </ng-container>
      </div>
    </section>
  `
})
export class AudiobookReviewPageComponent implements OnInit {
  detail: AudiobookDetail | null = null;
  loading = true;
  error: string | null = null;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly audiobookLibraryService: AudiobookLibraryService
  ) {}

  get readyAssets(): AudioAsset[] {
    return this.detail?.audioAssets.filter((asset) => asset.status === 'READY') ?? [];
  }

  get primaryAsset(): AudioAsset | null {
    return this.readyAssets.find((asset) => asset.type === 'FULL_AUDIOBOOK' || asset.type === 'PREVIEW_MP3') ?? this.readyAssets[0] ?? null;
  }

  get audioPartCards(): AudiobookPartCard[] {
    const detail = this.detail;
    if (!detail?.scenes) return [];

    return detail.scenes.map((scene, index) => {
      const parsed = this.parsePerformanceDirections(scene.performanceDirections);
      return {
        partNumber: index + 1,
        totalParts: detail.scenes.length,
        speakerName: scene.speakerName || parsed.speakerName || this.extractSpeakerFromFilename(scene),
        speakerRole: scene.speakerRoleDescription,
        voiceName: scene.voiceName,
        emotionTags: parsed.emotionTags,
        originalText: parsed.originalText,
        durationSeconds: scene.durationSeconds || undefined,
        status: scene.reviewStatus,
        audioUrl: this.getAudioStreamUrl(scene),
        readyAssetId: this.getReadyAssetId(scene),
      };
    });
  }

  private extractSpeakerFromFilename(scene: AudiobookScene): string | undefined {
    const readyAsset = this.detail?.audioAssets.find((a) => a.sceneId === scene.id && a.status === 'READY');
    if (!readyAsset) return undefined;

    // Try to extract speaker name from filename, e.g., "scene_1__narrator.wav" → "narrator"
    const filename = readyAsset.filename;
    const match = filename.match(/__([^_.]+)/);
    if (match && match[1]) {
      return match[1].charAt(0).toUpperCase() + match[1].slice(1);
    }
    return undefined;
  }

  private getAudioStreamUrl(scene: AudiobookScene): string | undefined {
    const readyAsset = this.detail?.audioAssets.find((a) => a.sceneId === scene.id && a.status === 'READY');
    return readyAsset?.streamUrl;
  }

  private getReadyAssetId(scene: AudiobookScene): string | undefined {
    const readyAsset = this.detail?.audioAssets.find((a) => a.sceneId === scene.id && a.status === 'READY');
    return readyAsset?.id;
  }

  private parsePerformanceDirections(performanceDirections?: string): {
    emotionTags: string[];
    speakerName?: string;
    originalText?: string;
  } {
    if (!performanceDirections) {
      return { emotionTags: [] };
    }

    const emotionTags: string[] = [];
    let remaining = performanceDirections;

    // Extract leading tags: [tag1] [tag2] [tag3] ...
    while (remaining.startsWith('[')) {
      const closeIndex = remaining.indexOf(']');
      if (closeIndex === -1) break;
      const tag = remaining.slice(1, closeIndex);
      emotionTags.push(tag);
      remaining = remaining.slice(closeIndex + 1).trim();
    }

    // Extract speaker name (typically first word before comma)
    let speakerName: string | undefined;
    let originalText: string | undefined;

    const speakerMatch = remaining.match(/^([^,\[]+),\s*/);
    if (speakerMatch) {
      speakerName = speakerMatch[1].trim();
      originalText = remaining.slice(speakerMatch[0].length);
    } else {
      // No comma found, try to extract first word as speaker if text follows
      const wordMatch = remaining.match(/^(\S+)\s+/);
      if (wordMatch && !wordMatch[1].startsWith('[')) {
        speakerName = wordMatch[1];
        originalText = remaining.slice(wordMatch[0].length).trim();
      } else {
        // No clear speaker name pattern, treat entire text as original
        originalText = remaining;
      }
    }

    return {
      emotionTags,
      speakerName,
      originalText: originalText ? originalText.trim() : undefined,
    };
  }

  async ngOnInit(): Promise<void> {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.error = 'The requested audiobook could not be found.';
      this.loading = false;
      return;
    }
    try {
      this.detail = await this.audiobookLibraryService.detail(id);
    } catch {
      this.error = 'The audiobook review page could not be loaded.';
    } finally {
      this.loading = false;
    }
  }

  trackAsset(_: number, asset: AudioAsset): string {
    return asset.id;
  }

  updatedLabel(value: string): string {
    return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
  }
}
