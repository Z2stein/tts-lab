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
        <div *ngIf="scenes.length === 0" style="color: red;">EMPTY SCENES!</div>
      </div>

      <ng-container *ngIf="scenes && scenes.length > 0">
        <div class="grid gap-3">
          <article *ngFor="let scene of scenes; let i = index" data-testid="scene-row">
            <p>Scene {{ i }}: {{ scene.title }}</p>
          </article>
        </div>
      </ng-container>
      <ng-container *ngIf="!scenes || scenes.length === 0">
        <div style="color: red;">No scenes to display</div>
      </ng-container>
    </section>
  `
})
export class AudiobookSceneListComponent {
  @Input() set scenes(value: AudiobookScene[] | undefined | null) {
    console.log('AudiobookSceneListComponent.scenes setter called with:', value);
    this._scenes = value || [];
  }
  get scenes(): AudiobookScene[] {
    return this._scenes;
  }
  private _scenes: AudiobookScene[] = [];

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
