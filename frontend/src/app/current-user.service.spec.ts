import { HttpHeaders, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { LoggerService } from './logger.service';
import { CurrentUserService, RequestRateLimitSummary } from './current-user.service';

describe('CurrentUserService', () => {
  let service: CurrentUserService;
  let httpMock: HttpTestingController;
  let logger: jasmine.SpyObj<LoggerService>;

  beforeEach(() => {
    logger = jasmine.createSpyObj<LoggerService>('LoggerService', ['info', 'warn', 'error']);

    TestBed.configureTestingModule({
      providers: [
        CurrentUserService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: LoggerService, useValue: logger }
      ]
    });

    service = TestBed.inject(CurrentUserService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('returns the authenticated user when /api/me responds with JSON', async () => {
    const promise = service.getCurrentUser();
    const req = httpMock.expectOne('/api/me');

    expect(req.request.method).toBe('GET');
    req.flush(JSON.stringify({
      id: 'user-1',
      email: 'u@test.dev',
      name: 'User',
      authMode: 'google',
      roles: ['USER']
    }), {
      status: 200,
      statusText: 'OK',
      headers: new HttpHeaders({ 'content-type': 'application/json' })
    });

    await expectAsync(promise).toBeResolvedTo({
      id: 'user-1',
      email: 'u@test.dev',
      name: 'User',
      authMode: 'google',
      roles: ['USER']
    });
  });

  it('returns null when /api/me responds with 401', async () => {
    const promise = service.getCurrentUser();
    const req = httpMock.expectOne('/api/me');
    req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    await expectAsync(promise).toBeResolvedTo(null);
  });

  it('returns null when /api/me responds with HTML instead of JSON', async () => {
    const promise = service.getCurrentUser();
    const req = httpMock.expectOne('/api/me');
    req.flush('<html><body>Sign in</body></html>', {
      status: 200,
      statusText: 'OK',
      headers: new HttpHeaders({ 'content-type': 'text/html' })
    });

    await expectAsync(promise).toBeResolvedTo(null);
  });

  it('dispatches request-limit updates when /api/request-limits/me returns JSON', async () => {
    const dispatchSpy = spyOn(window, 'dispatchEvent').and.callThrough();
    const promise = service.refreshRequestLimits();
    const req = httpMock.expectOne('/api/request-limits/me');

    const summary: RequestRateLimitSummary = {
      windowResetAt: '2026-05-07T12:00:00Z',
      windowSeconds: 60,
      limits: [
        {
          modelType: 'TEXT_MODEL',
          used: 3,
          limit: 10,
          remaining: 7,
          unit: 'WORDS'
        }
      ]
    };

    req.flush(JSON.stringify(summary), {
      status: 200,
      statusText: 'OK',
      headers: new HttpHeaders({ 'content-type': 'application/json' })
    });

    await expectAsync(promise).toBeResolvedTo(summary);
    expect(dispatchSpy).toHaveBeenCalled();
    expect((dispatchSpy.calls.mostRecent().args[0] as CustomEvent).detail).toEqual(summary);
  });

  it('posts to /logout when logging out', async () => {
    const navigateSpy = spyOn<any>(service as any, 'navigate').and.stub();
    const promise = service.startLogout();
    const req = httpMock.expectOne('/logout');

    expect(req.request.method).toBe('POST');
    req.flush('', { status: 204, statusText: 'No Content' });

    await expectAsync(promise).toBeResolved();
    expect(navigateSpy).toHaveBeenCalledWith('/');
  });
});
