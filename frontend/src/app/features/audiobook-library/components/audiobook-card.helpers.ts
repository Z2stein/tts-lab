import { AudioAssetResponse } from '../../../shared/api-contract.generated';

export function selectProjectPreviewAsset(audioAssets: AudioAssetResponse[]): AudioAssetResponse | null {
  const projectPreviewAssets = audioAssets.filter((asset) => isProjectPreviewAsset(asset));
  return projectPreviewAssets.find((asset) => asset.type === 'FULL_AUDIOBOOK') ?? projectPreviewAssets[0] ?? null;
}

export function isProjectPreviewAsset(asset: AudioAssetResponse): boolean {
  return asset.status === 'READY' && (asset.type === 'FULL_AUDIOBOOK' || asset.speechSegmentId === null);
}

export function buildPreviewFilename(title: string): string {
  const base = title.trim().replace(/[^a-z0-9]+/gi, '-').replace(/^-+|-+$/g, '');
  return `${(base || 'audiobook-preview').toLowerCase()}.mp3`;
}
