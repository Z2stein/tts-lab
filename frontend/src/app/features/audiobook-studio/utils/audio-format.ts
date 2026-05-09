import WaveSurfer from 'wavesurfer.js';

export function formatElapsedTime(milliseconds: number): string {
  const totalSeconds = Math.max(0, Math.floor(milliseconds / 1000));
  const minutes = Math.floor(totalSeconds / 60).toString().padStart(2, '0');
  const seconds = (totalSeconds % 60).toString().padStart(2, '0');
  return `${minutes}:${seconds}`;
}

export function durationLabelFor(waveSurfer: WaveSurfer | null): string {
  if (!waveSurfer) {
    return '00:00';
  }

  const duration = waveSurfer.getDuration();
  if (!Number.isFinite(duration) || duration <= 0) {
    return '00:00';
  }

  const minutes = Math.floor(duration / 60).toString().padStart(2, '0');
  const seconds = Math.floor(duration % 60).toString().padStart(2, '0');
  return `${minutes}:${seconds}`;
}
