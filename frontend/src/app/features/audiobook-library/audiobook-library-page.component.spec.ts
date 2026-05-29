import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { AudiobookLibraryPageComponent } from './audiobook-library-page.component';
import { AudiobookLibraryService } from './services/audiobook-library.service';
import { PageRevisitService } from '../../shared/services/page-revisit.service';
import { AudiobookSummary } from '../../shared/api-contract.generated';

function summary(id: string, title: string): AudiobookSummary {
  return {
    id,
    title,
    status: 'APPROVED',
    speechSegmentCount: 0,
    speakerCount: null,
    totalDurationSeconds: null,
    updatedAt: '2026-05-29T00:00:00Z',
    audioAssets: []
  } as AudiobookSummary;
}

describe('AudiobookLibraryPageComponent', () => {
  let fixture: ComponentFixture<AudiobookLibraryPageComponent>;
  let component: AudiobookLibraryPageComponent;
  let listSpy: jasmine.Spy<() => Promise<AudiobookSummary[]>>;
  let revisits$: Subject<void>;

  beforeEach(async () => {
    listSpy = jasmine.createSpy('list');
    revisits$ = new Subject<void>();

    await TestBed.configureTestingModule({
      imports: [AudiobookLibraryPageComponent],
      providers: [
        provideRouter([]),
        { provide: AudiobookLibraryService, useValue: { list: listSpy } },
        { provide: PageRevisitService, useValue: { revisits$ } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AudiobookLibraryPageComponent);
    component = fixture.componentInstance;
  });

  it('loads the library on init', async () => {
    listSpy.and.resolveTo([summary('1', 'First')]);

    await component.ngOnInit();

    expect(listSpy).toHaveBeenCalledTimes(1);
    expect(component.projects).toEqual([summary('1', 'First')]);
    expect(component.loading).toBeFalse();
  });

  it('re-fetches the library when the page is revisited', async () => {
    listSpy.and.resolveTo([summary('1', 'First')]);
    await component.ngOnInit();

    listSpy.and.resolveTo([summary('2', 'Second')]);
    revisits$.next();
    await Promise.resolve();

    expect(listSpy).toHaveBeenCalledTimes(2);
    expect(component.projects).toEqual([summary('2', 'Second')]);
  });

  it('keeps the current list when a background refresh fails', async () => {
    const consoleError = spyOn(console, 'error');
    listSpy.and.resolveTo([summary('1', 'First')]);
    await component.ngOnInit();

    listSpy.and.rejectWith(new Error('network down'));
    revisits$.next();
    await Promise.resolve();
    await Promise.resolve();

    expect(component.projects).toEqual([summary('1', 'First')]);
    expect(consoleError).toHaveBeenCalled();
  });

  it('unsubscribes from revisit notifications on destroy', async () => {
    listSpy.and.resolveTo([]);
    await component.ngOnInit();

    component.ngOnDestroy();
    revisits$.next();
    await Promise.resolve();

    expect(listSpy).toHaveBeenCalledTimes(1);
  });
});
