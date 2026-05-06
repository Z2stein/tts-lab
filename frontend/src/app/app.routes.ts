import { Routes } from '@angular/router';
import { HomePageComponent } from './features/home/home-page.component';
import { TextLengthPageComponent } from './features/text-length/text-length-page.component';
import { TtsWorkbenchPageComponent } from './features/tts-workbench/tts-workbench-page.component';

export const routes: Routes = [
  { path: '', component: HomePageComponent },
  { path: 'text-length', component: TextLengthPageComponent },
  { path: 'tts-workbench', component: TtsWorkbenchPageComponent },
  { path: '**', redirectTo: '' }
];
