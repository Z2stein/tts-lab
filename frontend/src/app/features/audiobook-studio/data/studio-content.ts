import { HeroCastMember, JourneyStep, SpeakerAccent } from '../models/audiobook-studio.types';

export const BENEFIT_CHIPS: readonly string[] = ['Multi-speaker', 'Speech segment detection', 'Voice previews', 'Export MP3'];

export const HERO_CAST: readonly HeroCastMember[] = [
  { name: 'Narrator', tone: 'warm calm', initials: 'N' },
  { name: 'Mara', tone: 'young tense', initials: 'M' },
  { name: 'Jonas', tone: 'soft nervous', initials: 'J' },
  { name: 'Station Keeper', tone: 'old gravelly', initials: 'SK' }
];

export const JOURNEY_STEPS: readonly JourneyStep[] = [
  {
    icon: '01',
    title: 'Add story',
    description: 'Paste your text. We\'ll keep the original wording and structure it for audio.',
    sectionId: 'story-section'
  },
  {
    icon: '02',
    title: 'Choose voices',
    description: 'We found the speakers in your story. Preview each voice or change it.',
    sectionId: 'cast-section'
  },
  {
    icon: '03',
    title: 'Review script',
    description: 'Check that each line is assigned to the correct speaker.',
    sectionId: 'script-section'
  },
  {
    icon: '04',
    title: 'Add emotion & pacing',
    description: 'Fine-tune how each line should be spoken, for example calm, urgent, or whispered.',
    sectionId: 'performance-section'
  },
  {
    icon: '05',
    title: 'Generate audiobook',
    description: 'Create a preview. You can still go back and edit before downloading.',
    sectionId: 'audio-section'
  }
];

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
  { label: 'English (US) - en-US', value: 'en-US' },
  { label: 'German (Germany) - de-DE', value: 'de-DE' },
  { label: 'French (France) - fr-FR', value: 'fr-FR' },
  { label: 'Spanish (Spain) - es-ES', value: 'es-ES' },
  { label: 'Japanese (Japan) - ja-JP', value: 'ja-JP' }
];

export const MODEL_NAME_OPTIONS: readonly string[] = [
  'gemini-3.1-flash-tts-preview',
  'gemini-2.5-pro-tts',
  'gemini-2.5-flash-tts'
];
