import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { AudiobookScene, AudioAsset } from '../models/audiobook-library.types';
import { parsePerformanceDirections } from '../utils/performance-parser';

@Component({
  selector: 'app-audiobook-scene-list',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './audiobook-scene-list.component.css',
  template: `
    <section class="rounded-lg border border-studio-line bg-studio-panel/85 p-5 backdrop-blur" data-testid="scene-list">
      <div class="mb-4">
        <p class="eyebrow">Scene details</p>
        <h2 class="m-0 text-2xl font-black">Performance notes</h2>
      </div>

      <ng-container *ngIf="scenes && scenes.length > 0">
        <div class="scene-list">
          <article *ngFor="let scene of scenes; let i = index" class="scene-item" data-testid="scene-row" [style.--speaker-accent]="getSpeakerColor(scene.speakerName)">
            <div class="scene-header">
              <span class="scene-number">{{ i + 1 }}.</span>
              <span class="scene-title">{{ scene.title }}</span>
            </div>

            <div class="scene-content">
              <div class="speaker-row">
                <span class="speaker-name">{{ scene.speakerName }}</span>
                <span *ngIf="scene.speakerRoleDescription" class="speaker-role">{{ scene.speakerRoleDescription }}</span>
              </div>

              <div class="emotion-tags">
                <span *ngFor="let tag of getEmotionTags(scene)" class="emotion-badge">{{ tag }}</span>
              </div>

              <div *ngIf="getOriginalText(scene)" class="scene-text">
                {{ getOriginalText(scene) }}
              </div>

              <div *ngIf="scene.voiceName" class="scene-meta">
                <span class="voice-label">Voice:</span> {{ scene.voiceName }}
              </div>
            </div>
          </article>
        </div>
      </ng-container>
      <ng-container *ngIf="!scenes || scenes.length === 0">
        <div class="empty-state">No scenes to display</div>
      </ng-container>
    </section>
  `
})
export class AudiobookSceneListComponent {
  @Input() set scenes(value: AudiobookScene[] | undefined | null) {
    this._scenes = value || [];
  }
  get scenes(): AudiobookScene[] {
    return this._scenes;
  }
  private _scenes: AudiobookScene[] = [];

  @Input() audioAssets: AudioAsset[] = [];

  private readonly speakerColors = [
    '#FF6B6B', '#4ECDC4', '#45B7D1',
    '#FFA07A', '#98D8C8', '#F7DC6F'
  ];

  readyAssets(sceneId: string): AudioAsset[] {
    return this.audioAssets.filter((asset) => asset.sceneId === sceneId && asset.status === 'READY');
  }

  durationLabel(seconds: number | null): string {
    if (seconds === null) return 'Duration pending';
    const minutes = Math.floor(seconds / 60);
    const remaining = seconds % 60;
    return `${minutes}:${remaining.toString().padStart(2, '0')}`;
  }

  getEmotionTags(scene: AudiobookScene): string[] {
    const parsed = parsePerformanceDirections(scene.performanceDirections);
    return parsed.emotionTags;
  }

  getOriginalText(scene: AudiobookScene): string | undefined {
    const parsed = parsePerformanceDirections(scene.performanceDirections);
    return parsed.originalText;
  }

  getSpeakerColor(speakerName?: string): string {
    if (!speakerName) return this.speakerColors[0];
    const hash = speakerName.charCodeAt(0);
    return this.speakerColors[hash % this.speakerColors.length];
  }
}
