import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { AudiobookScene, AudioAsset } from '../models/audiobook-library.types';

@Component({
  selector: 'app-audiobook-scene-list',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="rounded-lg border border-studio-line bg-studio-panel/85 p-5 backdrop-blur" data-testid="scene-list">
      <div class="mb-4 flex items-center justify-between gap-3">
        <div>
          <p class="eyebrow">Scene review</p>
          <h2 class="m-0 text-2xl font-black">Scenes</h2>
        </div>
        <span class="badge">{{ scenes.length }} total</span>
      </div>

      <div class="grid gap-3">
        <article *ngFor="let scene of scenes" class="grid gap-3 rounded-md border border-studio-line bg-studio-field p-4 sm:grid-cols-[1fr_auto] sm:items-center" data-testid="scene-row">
          <div>
            <p class="m-0 text-xs font-extrabold uppercase text-studio-accent">Scene {{ scene.orderIndex + 1 }}</p>
            <h3 class="m-0 mt-1 text-lg font-extrabold">{{ scene.title }}</h3>
            <p class="m-0 mt-1 text-sm text-studio-muted">{{ scene.reviewStatus.replace('_', ' ') }} · {{ durationLabel(scene.durationSeconds) }}</p>
          </div>
          <span class="badge">{{ readyAssets(scene.id).length }} ready asset{{ readyAssets(scene.id).length === 1 ? '' : 's' }}</span>
        </article>
      </div>
    </section>
  `
})
export class AudiobookSceneListComponent {
  @Input() scenes: AudiobookScene[] = [];
  @Input() audioAssets: AudioAsset[] = [];

  readyAssets(sceneId: string): AudioAsset[] {
    return this.audioAssets.filter((asset) => asset.sceneId === sceneId && asset.status === 'READY');
  }

  durationLabel(seconds: number | null): string {
    if (seconds === null) return 'Duration pending';
    const minutes = Math.floor(seconds / 60);
    const remaining = seconds % 60;
    return `${minutes}:${remaining.toString().padStart(2, '0')}`;
  }
}
