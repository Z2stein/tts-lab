import { HttpHeaders, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CurrentUserService } from '../../../current-user.service';
import { AudiobookApiService } from './audiobook-api.service';

describe('AudiobookApiService', () => {
  let service: AudiobookApiService;
  let httpMock: HttpTestingController;
  let currentUserService: jasmine.SpyObj<CurrentUserService>;

  beforeEach(() => {
    currentUserService = jasmine.createSpyObj<CurrentUserService>('CurrentUserService', ['refreshRequestLimits']);
    currentUserService.refreshRequestLimits.and.resolveTo(null);

    TestBed.configureTestingModule({
      providers: [
        AudiobookApiService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CurrentUserService, useValue: currentUserService }
      ]
    });

    service = TestBed.inject(AudiobookApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('posts JSON payloads and returns the parsed response', async () => {
    const promise = service.post<{ answer: string }>('/api/audiobooks/workflow/speaker-voice-analysis', { rawDialogue: 'Mara: Hello' }, 'Speaker voice analysis failed');
    const req = httpMock.expectOne('/api/audiobooks/workflow/speaker-voice-analysis');

    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ rawDialogue: 'Mara: Hello' });

    req.flush({ answer: 'done' });

    await expectAsync(promise).toBeResolvedTo({ answer: 'done' });
    expect(currentUserService.refreshRequestLimits).toHaveBeenCalled();
  });

  it('creates audio downloads with filename and project metadata', async () => {
    const promise = service.createAudio(
      {
        renderRequests: [
          {
            input: { text: 'Hello' },
            voice: { name: 'Kore' },
            audioConfig: {}
          }
        ]
      },
      {},
      'project-1'
    );
    const req = httpMock.expectOne('/api/audiobooks/workflow/create-audio?projectId=project-1');

    expect(req.request.method).toBe('POST');

    req.flush(new Blob(['audio-bytes'], { type: 'audio/mpeg' }), {
      headers: new HttpHeaders({
        'content-type': 'audio/mpeg',
        'content-disposition': 'attachment; filename="tts-render-request-2.mp3"',
        'x-audiobook-project-id': 'project-1'
      })
    });

    const result = await promise;
    expect(await result.blob.text()).toBe('audio-bytes');
    expect(result.filename).toBe('tts-render-request-2.mp3');
    expect(result.projectId).toBe('project-1');
    expect(currentUserService.refreshRequestLimits).toHaveBeenCalled();
  });

  it('maps API error responses into a stable message', async () => {
    const promise = service.post('/api/audiobooks/workflow/speaker-split-analysis', { rawDialogue: 'Mara: Hello' }, 'Speaker split analysis failed');
    const req = httpMock.expectOne('/api/audiobooks/workflow/speaker-split-analysis');

    req.flush({ message: 'Speaker split analysis failed from backend.' }, { status: 500, statusText: 'Server Error' });

    await expectAsync(promise).toBeRejectedWithError('Speaker split analysis failed from backend.');
    expect(currentUserService.refreshRequestLimits).toHaveBeenCalled();
  });

  it('reads blob error payloads when audio creation fails', async () => {
    const promise = service.createAudio(
      {
        renderRequests: [
          {
            input: { text: 'Hello' },
            voice: { name: 'Kore' },
            audioConfig: {}
          }
        ]
      }
    );
    const req = httpMock.expectOne('/api/audiobooks/workflow/create-audio');

    req.flush(new Blob([JSON.stringify({ message: 'Audio creation failed from backend.' })], { type: 'application/json' }), {
      status: 500,
      statusText: 'Server Error',
      headers: new HttpHeaders({ 'content-type': 'application/json' })
    });

    await expectAsync(promise).toBeRejectedWithError('Audio creation failed from backend.');
    expect(currentUserService.refreshRequestLimits).toHaveBeenCalled();
  });
});


