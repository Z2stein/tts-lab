import { Routes } from '@angular/router';
import { LandingPageComponent } from './features/home/landing-page.component';
import { TextLengthPageComponent } from './features/text-length/text-length-page.component';
import { TtsWorkbenchPageComponent } from './features/tts-workbench/tts-workbench-page.component';
import { PromptHistoryPageComponent } from './features/prompt-history/prompt-history-page.component';
import { AudiobookStudioPageComponent } from './features/audiobook-studio/audiobook-studio-page.component';
import { CvAudiobookDemoPageComponent } from './features/cv-audiobook-demo/cv-audiobook-demo-page.component';

export const routes: Routes = [
  { path: '', component: LandingPageComponent, title: 'TTS Lab' },
  { path: 'cv-audiobook-demo', component: CvAudiobookDemoPageComponent, title: 'AI Audiobook Creator Demo | TTS Lab' },
  { path: 'audiobook-studio', component: AudiobookStudioPageComponent, title: 'Audiobook Studio | TTS Lab' },
  { path: 'text-length', component: TextLengthPageComponent, title: 'Text Length | TTS Lab' },
  { path: 'tts-workbench', component: TtsWorkbenchPageComponent, title: 'TTS Workbench | TTS Lab' },
  { path: 'prompt-history', component: PromptHistoryPageComponent, title: 'Prompt History | TTS Lab' },
  { path: '**', redirectTo: '' }
];
