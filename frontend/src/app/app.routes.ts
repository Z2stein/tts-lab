import { Routes } from '@angular/router';
import { LandingPageComponent } from './features/home/landing-page.component';
import { PromptHistoryPageComponent } from './features/prompt-history/prompt-history-page.component';
import { AudiobookStudioLandingPageComponent } from './features/audiobook-studio/audiobook-studio-landing-page.component';
import { AudiobookStudioResumePageComponent } from './features/audiobook-studio/audiobook-studio-resume-page.component';
import { AudiobookLibraryPageComponent } from './features/audiobook-library/audiobook-library-page.component';
import { CvAudiobookDemoPageComponent } from './features/cv-audiobook-demo/cv-audiobook-demo-page.component';

export const routes: Routes = [
  { path: '', component: LandingPageComponent, title: 'TTS Lab' },
  { path: 'cv-audiobook-demo', component: CvAudiobookDemoPageComponent, title: 'AI Audiobook Creator Demo | TTS Lab' },
  { path: 'audiobook-studio', component: AudiobookStudioLandingPageComponent, title: 'Audiobook Studio | TTS Lab' },
  { path: 'audiobook-studio/:projectId', component: AudiobookStudioResumePageComponent, title: 'Audiobook Studio | TTS Lab' },
  { path: 'audiobook-library', component: AudiobookLibraryPageComponent, title: 'My Audiobooks | TTS Lab' },
  { path: 'audiobook-library/:id', redirectTo: 'audiobook-studio/:id', pathMatch: 'full' },
  { path: 'library', redirectTo: 'audiobook-library', pathMatch: 'full' },
  { path: 'prompt-history', component: PromptHistoryPageComponent, title: 'Prompt History | TTS Lab' },
  { path: '**', redirectTo: '' }
];
