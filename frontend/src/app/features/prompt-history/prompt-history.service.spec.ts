import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PromptHistoryService } from './prompt-history.service';

describe('PromptHistoryService', () => {
  let service: PromptHistoryService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [PromptHistoryService, provideHttpClient(), provideHttpClientTesting()]
    });

    service = TestBed.inject(PromptHistoryService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('returns prompt history items and sends the modelType query parameter', async () => {
    const promise = service.getHistory('SPEECH_MODEL');
    const req = httpMock.expectOne('/api/prompts/history?modelType=SPEECH_MODEL');

    expect(req.request.method).toBe('GET');
    req.flush({
      items: [
        {
          id: 1,
          userId: 'user-1',
          userEmail: 'u@test.dev',
          modelType: 'SPEECH_MODEL',
          providerModelName: 'mock',
          promptText: 'Hello',
          requestStatus: 'SUCCESS',
          createdAt: '2026-05-07T12:00:00Z'
        }
      ]
    });

    await expectAsync(promise).toBeResolvedTo([
      {
        id: 1,
        userId: 'user-1',
        userEmail: 'u@test.dev',
        modelType: 'SPEECH_MODEL',
        providerModelName: 'mock',
        promptText: 'Hello',
        requestStatus: 'SUCCESS',
        createdAt: '2026-05-07T12:00:00Z'
      }
    ]);
  });

  it('throws a stable error message when history loading fails', async () => {
    const promise = service.getHistory();
    const req = httpMock.expectOne('/api/prompts/history');

    req.flush({ message: 'boom' }, { status: 503, statusText: 'Service Unavailable' });

    await expectAsync(promise).toBeRejectedWithError('Prompt history failed (HTTP 503).');
  });
});
