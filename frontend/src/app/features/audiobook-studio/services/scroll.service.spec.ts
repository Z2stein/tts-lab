import { TestBed } from '@angular/core/testing';
import { ScrollService } from './scroll.service';

describe('ScrollService', () => {
  let service: ScrollService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [ScrollService] });
    service = TestBed.inject(ScrollService);
  });

  it('calls scrollIntoView on the matching element', () => {
    const el = document.createElement('div');
    el.id = 'my-section';
    document.body.appendChild(el);
    const spy = spyOn(el, 'scrollIntoView');

    service.scrollTo('my-section');

    expect(spy).toHaveBeenCalledWith({ behavior: 'smooth', block: 'start' });
    el.remove();
  });

  it('does nothing for an unknown id', () => {
    expect(() => service.scrollTo('does-not-exist')).not.toThrow();
  });

  it('calls focus on the matching element', () => {
    const el = document.createElement('button');
    el.id = 'my-btn';
    document.body.appendChild(el);
    const spy = spyOn(el, 'focus');

    service.focusById('my-btn', { preventScroll: true });

    expect(spy).toHaveBeenCalledWith({ preventScroll: true });
    el.remove();
  });
});
