import { Routes } from '@angular/router';
import { LandingPageComponent } from './features/home/landing-page.component';
import { PromptHistoryPageComponent } from './features/prompt-history/prompt-history-page.component';
import { AudiobookStudioPageComponent } from './features/audiobook-studio/audiobook-studio-page.component';
import { AudiobookLibraryPageComponent } from './features/audiobook-library/audiobook-library-page.component';
import { AudiobookReviewPageComponent } from './features/audiobook-library/audiobook-review-page.component';
import { CvAudiobookDemoPageComponent } from './features/cv-audiobook-demo/cv-audiobook-demo-page.component';

export const routes: Routes = [
  { path: '', component: LandingPageComponent, title: 'TTS Lab' },
  { path: 'cv-audiobook-demo', component: CvAudiobookDemoPageComponent, title: 'AI Audiobook Creator Demo | TTS Lab' },
  { path: 'audiobook-studio', component: AudiobookStudioPageComponent, title: 'Audiobook Studio | TTS Lab' },
  { path: 'audiobook-library', component: AudiobookLibraryPageComponent, title: 'My Audiobooks | TTS Lab' },
  { path: 'audiobook-library/:id', component: AudiobookReviewPageComponent, title: 'Audiobook Review | TTS Lab' },
  { path: 'library', redirectTo: 'audiobook-library', pathMatch: 'full' },
  { path: 'prompt-history', component: PromptHistoryPageComponent, title: 'Prompt History | TTS Lab' },
  { path: '**', redirectTo: '' }
];
