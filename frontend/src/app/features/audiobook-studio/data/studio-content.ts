import { HeroCastMember, SpeakerAccent, WorkflowStepKey, WorkflowStepperContent } from '../models/audiobook-studio.types';

export const BENEFIT_CHIPS: readonly string[] = ['Multi-speaker', 'Speech segment detection', 'Voice previews', 'Export MP3'];

export const HERO_CAST: readonly HeroCastMember[] = [
  { name: 'Narrator', tone: 'warm calm', initials: 'N', imageUrl: '/assets/voices/zephyr/avatar.png' },
  { name: 'Mara', tone: 'young tense', initials: 'M', imageUrl: '/assets/voices/puck/avatar.png' },
  { name: 'Jonas', tone: 'soft nervous', initials: 'J', imageUrl: '/assets/voices/charon/avatar.png' },
  { name: 'Station Keeper', tone: 'old gravelly', initials: 'SK', imageUrl: '/assets/voices/algenib/avatar.png' }
];

export const WORKFLOW_STEPPER_CONTENT: Readonly<Record<WorkflowStepKey, WorkflowStepperContent>> = {
  story: {
    number: 1,
    title: 'Add story',
    description: 'Paste your text. We keep the original wording and structure it for audio.'
  },
  cast: {
    number: 2,
    title: 'Speaker',
    description: 'We found the speakers in your story. Preview each voice or change it.'
  },
  script: {
    number: 3,
    title: 'Review script',
    description: 'Check that each line is assigned to the correct speaker.'
  },
  performance: {
    number: 4,
    title: 'Performance',
    description: 'Fine-tune how each line should be spoken. Adjust emotion, pacing, and emphasis.'
  },
  audio: {
    number: 5,
    title: 'Generate',
    description: 'Create a preview. You can still go back and edit before downloading.'
  }
};

export const SPEAKER_ACCENTS: readonly SpeakerAccent[] = [
  { color: '#f0ad5d', shadow: 'rgba(240, 173, 93, 0.34)' },
  { color: '#c965ff', shadow: 'rgba(201, 101, 255, 0.34)' },
  { color: '#48b5ff', shadow: 'rgba(72, 181, 255, 0.32)' },
  { color: '#8fe77a', shadow: 'rgba(143, 231, 122, 0.3)' },
  { color: '#ff7da8', shadow: 'rgba(255, 125, 168, 0.3)' },
  { color: '#7de7d4', shadow: 'rgba(125, 231, 212, 0.3)' }
];

export const SAMPLE_STORY = `Narrator: The last train had already left when Mara found the brass key under the station clock.
Mara: Jonas, tell me you did not hide this here all winter.
Jonas: I was protecting it. The map said the keeper would know when the hour came.
Station Keeper: The hour came ten minutes ago, and the tunnels are listening.
Narrator: A warm light moved beneath the platform boards, slow as a waking ember.
Mara: Then we go now.
Jonas: Together?
Station Keeper: Together, and quietly. Stories travel faster underground.`;

export const LANGUAGE_CODE_OPTIONS: readonly { label: string; value: string }[] = [
  { label: 'Arabic (Egypt) - ar-EG', value: 'ar-EG' },
  { label: 'Dutch (Netherlands) - nl-NL', value: 'nl-NL' },
  { label: 'English (India) - en-IN', value: 'en-IN' },
  { label: 'English (US) - en-US', value: 'en-US' },
  { label: 'French (France) - fr-FR', value: 'fr-FR' },
  { label: 'German (Germany) - de-DE', value: 'de-DE' },
  { label: 'Hindi (India) - hi-IN', value: 'hi-IN' },
  { label: 'Indonesian (Indonesia) - id-ID', value: 'id-ID' },
  { label: 'Italian (Italy) - it-IT', value: 'it-IT' },
  { label: 'Spanish (Spain) - es-ES', value: 'es-ES' },
  { label: 'Japanese (Japan) - ja-JP', value: 'ja-JP' },
  { label: 'Korean (South Korea) - ko-KR', value: 'ko-KR' },
  { label: 'Marathi (India) - mr-IN', value: 'mr-IN' },
  { label: 'Polish (Poland) - pl-PL', value: 'pl-PL' },
  { label: 'Portuguese (Brazil) - pt-BR', value: 'pt-BR' },
  { label: 'Romanian (Romania) - ro-RO', value: 'ro-RO' },
  { label: 'Russian (Russia) - ru-RU', value: 'ru-RU' },
  { label: 'Tamil (India) - ta-IN', value: 'ta-IN' },
  { label: 'Telugu (India) - te-IN', value: 'te-IN' },
  { label: 'Thai (Thailand) - th-TH', value: 'th-TH' },
  { label: 'Turkish (Turkey) - tr-TR', value: 'tr-TR' },
  { label: 'Ukrainian (Ukraine) - uk-UA', value: 'uk-UA' },
  { label: 'Vietnamese (Vietnam) - vi-VN', value: 'vi-VN' }
];

export const MODEL_NAME_OPTIONS: readonly string[] = [
  'gemini-3.1-flash-tts-preview',
  'gemini-2.5-pro-tts',
  'gemini-2.5-flash-tts'
];
