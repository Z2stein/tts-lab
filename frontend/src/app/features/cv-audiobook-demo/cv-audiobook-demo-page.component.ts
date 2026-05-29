import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DemoAudioPreviewComponent } from './demo-audio-preview.component';

interface DemoAction {
  label: string;
  route: string;
}

interface ExternalDemoAction {
  label: string;
  href: string;
  ariaLabel: string;
}

interface TechnicalFeatureCard {
  id: string;
  title: string;
  description: string;
  proof: string[];
}

@Component({
  selector: 'app-cv-audiobook-demo-page',
  standalone: true,
  imports: [RouterLink, DemoAudioPreviewComponent],
  templateUrl: './cv-audiobook-demo-page.component.html'
})
export class CvAudiobookDemoPageComponent {
  readonly projectLabel = 'AI AUDIOBOOK STUDIO';
  readonly headlinePrefix = 'Create You Own';
  readonly headlineAccent = 'Cinematic Audiobook';
  readonly headlineSuffix = '';
  readonly subtitle = 'Stop settling for boring audiobooks. Bring your own story to life! Write or paste a text. Watch our AI automatically cast characters, assign distinct voices, and transform your words into a fully immersive audio experience in minutes.';
  readonly demoLabel = 'Demo preview';
  readonly demoAudioSrc = '/assets/audio/voice-samples/full-text-preview.mp3';
  readonly primaryAction: DemoAction = {
    label: 'Try the Audiobook Creator',
    route: '/audiobook-studio'
  };
  readonly secondaryAction: ExternalDemoAction = {
    label: 'Z2stein/tts-lab',
    href: 'https://github.com/Z2stein/tts-lab',
    ariaLabel: 'View Z2stein/tts-lab on GitHub'
  };
  readonly technicalFeatureCards: TechnicalFeatureCard[] = [
    {
      id: 'ai-workflow-orchestration',
      title: 'AI workflow orchestration',
      description: 'The audiobook flow is split into reviewable stages instead of hiding everything behind one opaque AI call.',
      proof: [
        'Separate backend steps cover speaker analysis, speaker split, emotion annotation, render planning, and audio creation.',
        'The final request preview and render-plan endpoints expose the provider-shaped JSON before generation runs.',
        'Per-part audio generation is finalized separately from the merged preview that is shown to the user.'
      ]
    },
    {
      id: 'human-in-the-loop-workflow',
      title: 'Human-in-the-loop product workflow',
      description: 'Users can correct cast names, script turns, and production settings before later stages run.',
      proof: [
        'Cast editing and script editing are handled in focused Angular components and a central studio facade.',
        'Script edits mark performance notes stale so downstream steps are forced to refresh.',
        'The resume route reloads a saved workflow snapshot so an in-progress project can continue later.'
      ]
    },
    {
      id: 'backend-reliability',
      title: 'Backend reliability and safe operations',
      description: 'The backend returns structured errors, request IDs, auth modes, and usage limits instead of leaking internals.',
      proof: [
        'Global exception handling emits safe JSON responses with a request ID header.',
        'Auth and demo-token configuration are validated so invalid environment setup fails fast.',
        'Request limits and prompt history are persisted per user and per model.'
      ]
    },
    {
      id: 'contract-driven-development',
      title: 'Contract-driven fullstack development',
      description: 'Frontend and backend stay aligned through a shared OpenAPI contract and reusable fixtures.',
      proof: [
        'The shared OpenAPI file is the source of truth for generated frontend types.',
        'Backend controller tests assert responses against that same contract.',
        'Shared fixtures in test-contracts are reused by frontend and backend tests.'
      ]
    },
    {
      id: 'branch-aware-deployment',
      title: 'Branch-aware deployment pipeline',
      description: 'The repo can deploy preview environments by branch and wait for rollout readiness before it reports success.',
      proof: [
        'GitHub Actions builds and pushes the frontend and backend images.',
        'Helm deploys frontend, backend, PostgreSQL, and ingress with probe checks.',
        'Shared scripts derive branch slugs, namespaces, release names, and preview hosts.'
      ]
    },
    {
      id: 'testing-quality-gates',
      title: 'Testing and quality gates',
      description: 'The page and backend are protected by unit tests, contract checks, builds, linting, and Playwright E2E.',
      proof: [
        'Angular/Karma, ESLint, and build scripts are part of the frontend workflow.',
        'Playwright covers mocked UI flows and a real backend health check.',
        'Backend tests validate controller behavior, contract shape, and error handling.'
      ]
    },
    {
      id: 'provider-abstraction',
      title: 'AI provider abstraction and safe local mode',
      description: 'The AI path is mockable, and the frontend only talks to backend APIs.',
      proof: [
        'Chat and audiobook workflows switch between mock and Gemini provider modes in the backend.',
        'Deterministic fallback services keep the workflow usable without live provider calls.',
        'Real TTS generation depends on optional backend credentials; the mock mode remains available for local work.'
      ]
    }
  ];

  readonly waveformBars = [
    18, 30, 42, 54, 36, 48, 70, 46, 58, 88, 104, 64,
    52, 76, 62, 44, 38, 58, 72, 48, 34, 56, 66, 48,
    40, 62, 80, 56, 44, 32, 28, 46, 60, 38, 26, 22
  ];
}
