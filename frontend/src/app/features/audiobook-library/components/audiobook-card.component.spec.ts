import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { AudiobookCardComponent } from './audiobook-card.component';
import { AudiobookSummary, AudioAssetResponse } from '../../../shared/api-contract.generated';

describe('AudiobookCardComponent', () => {
  let fixture: ComponentFixture<AudiobookCardComponent>;
  let component: AudiobookCardComponent;

  const previewAsset = {
    id: 'asset-preview',
    speechSegmentId: null,
    type: 'PREVIEW_MP3',
    version: 1,
    filename: 'hidden-signal-preview.mp3',
    contentType: 'audio/mpeg',
    sizeBytes: 123456,
    durationSeconds: 185,
    status: 'READY',
    createdAt: '2026-05-09T10:30:00Z',
    downloadUrl: '/api/audiobooks/project-1/audio-assets/asset-preview/download',
    streamUrl: '/api/audiobooks/project-1/audio-assets/asset-preview/stream'
  } as const;

  const segmentAsset = {
    id: 'asset-segment',
    speechSegmentId: 'segment-1',
    type: 'PREVIEW_MP3',
    version: 1,
    filename: 'segment-1-narrator.mp3',
    contentType: 'audio/mpeg',
    sizeBytes: 45678,
    durationSeconds: 30,
    status: 'READY',
    createdAt: '2026-05-09T10:20:00Z',
    downloadUrl: '/api/audiobooks/project-1/audio-assets/asset-segment/download',
    streamUrl: '/api/audiobooks/project-1/audio-assets/asset-segment/stream'
  } as const;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AudiobookCardComponent, RouterTestingModule]
    }).compileComponents();

    fixture = TestBed.createComponent(AudiobookCardComponent);
    component = fixture.componentInstance;
  });

  it('renders project counts and selects the project preview asset', () => {
    component.project = {
      id: 'project-1',
      title: 'The Hidden Signal',
      status: 'NEEDS_REVIEW',
      speechSegmentCount: 12,
      speakerCount: 2,
      totalDurationSeconds: 185,
      updatedAt: '2026-05-09T10:30:00Z',
      audioAssets: [segmentAsset, previewAsset]
    } as AudiobookSummary;

    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="speech-segment-count"]').textContent).toContain('12');
    expect(fixture.nativeElement.querySelector('[data-testid="speaker-count"]').textContent).toContain('2');
    expect(component.previewAsset?.id).toBe('asset-preview');
  });

  it('emits the project preview asset for playback and downloads the preview file name', () => {
    component.project = {
      id: 'project-1',
      title: 'The Hidden Signal',
      status: 'NEEDS_REVIEW',
      speechSegmentCount: 12,
      speakerCount: 2,
      totalDurationSeconds: 185,
      updatedAt: '2026-05-09T10:30:00Z',
      audioAssets: [segmentAsset, previewAsset]
    } as AudiobookSummary;

    fixture.detectChanges();

    const emitted: AudioAssetResponse[] = [];
    component.playPreview.subscribe((asset) => emitted.push(asset));

    component.onPlayPreview();
    expect(emitted).toEqual([previewAsset]);

    const anchor = document.createElement('a');
    spyOn(document, 'createElement').and.returnValue(anchor);
    spyOn(anchor, 'click');

    component.onDownloadPreview();

    expect(anchor.href).toContain(previewAsset.downloadUrl);
    expect(anchor.download).toBe('the-hidden-signal.mp3');
    expect(anchor.click).toHaveBeenCalled();
  });

  it('disables preview actions when only segment assets exist', () => {
    component.project = {
      id: 'project-1',
      title: 'The Hidden Signal',
      status: 'NEEDS_REVIEW',
      speechSegmentCount: 3,
      speakerCount: 2,
      totalDurationSeconds: 19,
      updatedAt: '2026-05-09T10:30:00Z',
      audioAssets: [segmentAsset]
    } as AudiobookSummary;

    fixture.detectChanges();

    expect(component.previewAsset).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="play-preview"]').disabled).toBeTrue();
    expect(fixture.nativeElement.querySelector('[data-testid="download-preview"]').disabled).toBeTrue();
  });
});
