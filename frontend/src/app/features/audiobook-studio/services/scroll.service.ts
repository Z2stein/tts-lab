import { Injectable } from '@angular/core';

@Injectable()
export class ScrollService {
  scrollTo(sectionId: string): void {
    document.getElementById(sectionId)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  focusById(elementId: string, options?: FocusOptions): void {
    (document.getElementById(elementId) as HTMLElement | null)?.focus(options);
  }
}
