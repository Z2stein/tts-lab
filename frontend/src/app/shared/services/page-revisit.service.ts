import { DOCUMENT } from '@angular/common';
import { Inject, Injectable } from '@angular/core';
import { EMPTY, fromEvent, merge, Observable } from 'rxjs';
import { filter, map } from 'rxjs/operators';

/**
 * Emits whenever the user returns to an already-open page: either the tab/app
 * becomes visible again, or the page is restored from the back/forward cache
 * (which is how iOS home-screen web apps resume from a frozen snapshot).
 *
 * Components can subscribe to silently refetch data so already-open devices do
 * not keep showing stale content without a hard reload.
 */
@Injectable({ providedIn: 'root' })
export class PageRevisitService {
  readonly revisits$: Observable<void>;

  constructor(@Inject(DOCUMENT) private readonly document: Document) {
    const view = this.document.defaultView;

    const becameVisible$ = fromEvent(this.document, 'visibilitychange').pipe(
      filter(() => this.document.visibilityState === 'visible')
    );

    const restoredFromCache$ = view
      ? fromEvent<PageTransitionEvent>(view, 'pageshow').pipe(filter((event) => event.persisted))
      : EMPTY;

    this.revisits$ = merge(becameVisible$, restoredFromCache$).pipe(map(() => undefined));
  }
}
