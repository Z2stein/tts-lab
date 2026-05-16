import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AudiobookLibraryService } from './audiobook-library.service';

describe('AudiobookLibraryService', () => {
  let service: AudiobookLibraryService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AudiobookLibraryService, provideHttpClient(), provideHttpClientTesting()]
    });

    service = TestBed.inject(AudiobookLibraryService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('lists audiobook summaries', async () => {
    const promise = service.list();
    const req = httpMock.expectOne('/api/audiobooks');

    expect(req.request.method).toBe('GET');
    req.flush({
      items: [
        {
          id: 'project-1',
          title: 'First audiobook',
          status: 'READY'
        }
      ]
    });

    await expectAsync(promise).toBeResolvedTo([
      {
        id: 'project-1',
        title: 'First audiobook',
        status: 'READY'
      }
    ] as any);
  });

  it('loads audiobook details', async () => {
    const promise = service.detail('project-1');
    const req = httpMock.expectOne('/api/audiobooks/project-1');

    expect(req.request.method).toBe('GET');
    req.flush({
      id: 'project-1',
      title: 'First audiobook'
    });

    await expectAsync(promise).toBeResolvedTo({
      id: 'project-1',
      title: 'First audiobook'
    } as any);
  });

  it('updates audiobook titles', async () => {
    const promise = service.updateTitle('project-1', 'Updated title');
    const req = httpMock.expectOne('/api/audiobooks/project-1');

    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ title: 'Updated title' });
    req.flush({
      id: 'project-1',
      title: 'Updated title'
    });

    await expectAsync(promise).toBeResolvedTo({
      id: 'project-1',
      title: 'Updated title'
    } as any);
  });

  it('throws a stable error message when listing fails', async () => {
    const promise = service.list();
    const req = httpMock.expectOne('/api/audiobooks');

    req.flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });

    await expectAsync(promise).toBeRejectedWithError('Audiobook library failed (HTTP 500).');
  });
});
