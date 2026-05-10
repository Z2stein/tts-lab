import { Injectable } from '@angular/core';
import {
  AudiobookDetail,
  AudiobookLibraryDisplay,
  AudiobookSummary,
  AudiobookSummaryResponse,
  Character,
  SpeechSegment,
  AIGenerationRun,
  AudioAsset,
  AudiobookProject,
} from '../models/audiobook-library.types';

@Injectable({ providedIn: 'root' })
export class AudiobookLibraryService {
  async list(): Promise<AudiobookSummary[]> {
    const response = await fetch('/api/audiobooks');
    if (!response.ok) {
      throw new Error(`Audiobook library failed (HTTP ${response.status}).`);
    }
    const data = (await response.json()) as AudiobookSummaryResponse;
    return data.items;
  }

  async detail(id: string): Promise<AudiobookDetail> {
    const response = await fetch(`/api/audiobooks/${encodeURIComponent(id)}`);
    if (!response.ok) {
      throw new Error(`Audiobook detail failed (HTTP ${response.status}).`);
    }
    return (await response.json()) as AudiobookDetail;
  }

  // NEW: Get enriched library display with generation history
  async getLibraryDisplay(projectId: string): Promise<AudiobookLibraryDisplay> {
    const response = await fetch(`/api/audiobooks/${encodeURIComponent(projectId)}/library`);
    if (!response.ok) {
      throw new Error(`Audiobook library display failed (HTTP ${response.status}).`);
    }
    return (await response.json()) as AudiobookLibraryDisplay;
  }

  // Project operations
  async getProject(projectId: string): Promise<AudiobookProject> {
    const response = await fetch(`/api/audiobooks/${encodeURIComponent(projectId)}`);
    if (!response.ok) {
      throw new Error(`Get project failed (HTTP ${response.status}).`);
    }
    return (await response.json()) as AudiobookProject;
  }

  // Character operations
  async getCharacters(projectId: string): Promise<Character[]> {
    const response = await fetch(`/api/audiobooks/${encodeURIComponent(projectId)}/characters`);
    if (!response.ok) {
      throw new Error(`Get characters failed (HTTP ${response.status}).`);
    }
    return (await response.json()) as Character[];
  }

  // Segment operations
  async getSegments(projectId: string): Promise<SpeechSegment[]> {
    const response = await fetch(`/api/audiobooks/${encodeURIComponent(projectId)}/segments`);
    if (!response.ok) {
      throw new Error(`Get segments failed (HTTP ${response.status}).`);
    }
    return (await response.json()) as SpeechSegment[];
  }

  // Generation run operations
  async getGenerationRuns(projectId: string): Promise<AIGenerationRun[]> {
    const response = await fetch(`/api/audiobooks/${encodeURIComponent(projectId)}/generation-runs`);
    if (!response.ok) {
      throw new Error(`Get generation runs failed (HTTP ${response.status}).`);
    }
    return (await response.json()) as AIGenerationRun[];
  }

  async getGenerationRun(projectId: string, runId: string): Promise<AIGenerationRun> {
    const response = await fetch(
      `/api/audiobooks/${encodeURIComponent(projectId)}/generation-runs/${encodeURIComponent(runId)}`
    );
    if (!response.ok) {
      throw new Error(`Get generation run failed (HTTP ${response.status}).`);
    }
    return (await response.json()) as AIGenerationRun;
  }

  // Asset operations
  async getAssets(projectId: string): Promise<AudioAsset[]> {
    const display = await this.getLibraryDisplay(projectId);
    return display.assets;
  }

  // Download/stream helpers
  getDownloadUrl(projectId: string, assetId: string): string {
    return `/api/audiobooks/${encodeURIComponent(projectId)}/audio-assets/${encodeURIComponent(assetId)}/download`;
  }

  getStreamUrl(projectId: string, assetId: string): string {
    return `/api/audiobooks/${encodeURIComponent(projectId)}/audio-assets/${encodeURIComponent(assetId)}/stream`;
  }
}
