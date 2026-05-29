import { TestBed } from '@angular/core/testing';
import { Subscription } from 'rxjs';
import { PageRevisitService } from './page-revisit.service';

describe('PageRevisitService', () => {
  let service: PageRevisitService;
  let subscription: Subscription;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(PageRevisitService);
  });

  afterEach(() => {
    subscription?.unsubscribe();
  });

  it('emits when the document becomes visible again', () => {
    let count = 0;
    subscription = service.revisits$.subscribe(() => count++);

    document.dispatchEvent(new Event('visibilitychange'));

    expect(count).toBe(1);
  });

  it('emits when the page is restored from the back/forward cache', () => {
    let count = 0;
    subscription = service.revisits$.subscribe(() => count++);

    window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true }));

    expect(count).toBe(1);
  });

  it('does not emit on a fresh page load (pageshow without restore)', () => {
    let count = 0;
    subscription = service.revisits$.subscribe(() => count++);

    window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: false }));

    expect(count).toBe(0);
  });
});
