import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CvAudiobookDemoPageComponent } from './cv-audiobook-demo-page.component';

describe('CvAudiobookDemoPageComponent', () => {
  let fixture: ComponentFixture<CvAudiobookDemoPageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CvAudiobookDemoPageComponent],
      providers: [provideRouter([])]
    }).compileComponents();

    fixture = TestBed.createComponent(CvAudiobookDemoPageComponent);
    fixture.detectChanges();
  });

  it('shows a repository-named GitHub link in the technical features header', () => {
    const link = fixture.nativeElement.querySelector('[data-testid="cv-demo-github-link"]') as HTMLAnchorElement | null;

    expect(link).not.toBeNull();
    expect(link?.textContent).toContain('Z2stein/tts-lab');
    expect(link?.href).toBe('https://github.com/Z2stein/tts-lab');
    expect(link?.target).toBe('_blank');
    expect(link?.rel).toContain('noopener');
    expect(link?.rel).toContain('noreferrer');
  });
});
