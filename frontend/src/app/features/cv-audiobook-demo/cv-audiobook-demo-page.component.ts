import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DemoAudioPreviewComponent } from './demo-audio-preview.component';

interface DemoAction {
  label: string;
  route: string;
  fragment?: string;
}

@Component({
  selector: 'app-cv-audiobook-demo-page',
  standalone: true,
  imports: [RouterLink, DemoAudioPreviewComponent],
  templateUrl: './cv-audiobook-demo-page.component.html'
})
export class CvAudiobookDemoPageComponent {
  readonly projectLabel = 'Private AI / Audiobook Project';
  readonly headlinePrefix = 'I built an';
  readonly headlineAccent = 'AI audiobook';
  readonly headlineSuffix = 'creator.';
  readonly subtitle = 'Paste a story excerpt with dialogue. The app detects characters, suggests voices, adds performance notes, and turns it into audiobook-style audio.';
  readonly demoLabel = 'Demo preview';
  readonly demoAudioSrc = '/assets/audio/voice-samples/full-text-preview.mp3';
  readonly primaryAction: DemoAction = {
    label: 'Try the Audiobook Creator',
    route: '/audiobook-studio'
  };
  readonly secondaryAction: DemoAction = {
    label: 'View technical details',
    route: '/cv-audiobook-demo',
    fragment: 'technical-details'
  };
  readonly technicalStack = [
    'Angular',
    'Spring Boot',
    'Kubernetes',
    'Helm',
    'GitHub Actions',
    'AI/TTS provider integration'
  ];

  readonly waveformBars = [
    18, 30, 42, 54, 36, 48, 70, 46, 58, 88, 104, 64,
    52, 76, 62, 44, 38, 58, 72, 48, 34, 56, 66, 48,
    40, 62, 80, 56, 44, 32, 28, 46, 60, 38, 26, 22
  ];
}
