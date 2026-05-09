import { HeroCastMember, JourneyStep, SpeakerAccent } from '../models/audiobook-studio.types';

export const BENEFIT_CHIPS: readonly string[] = ['Multi-speaker', 'Scene detection', 'Voice previews', 'Export MP3'];

export const HERO_CAST: readonly HeroCastMember[] = [
  { name: 'Narrator', tone: 'warm calm', initials: 'N' },
  { name: 'Mara', tone: 'young tense', initials: 'M' },
  { name: 'Jonas', tone: 'soft nervous', initials: 'J' },
  { name: 'Station Keeper', tone: 'old gravelly', initials: 'SK' }
];

export const JOURNEY_STEPS: readonly JourneyStep[] = [
  {
    icon: '01',
    title: 'Paste your story',
    description: 'Drop in a chapter, scene, or script and keep the original story flow intact.',
    sectionId: 'story-section'
  },
  {
    icon: '02',
    title: 'Discover the cast',
    description: 'AI identifies the narrator and characters, then suggests fitting voice directions.',
    sectionId: 'cast-section'
  },
  {
    icon: '03',
    title: 'Direct the performance',
    description: 'Review dialogue, approve pacing, and add emotional notes before production.',
    sectionId: 'script-section'
  },
  {
    icon: '04',
    title: 'Generate audio',
    description: 'Create a multi-speaker MP3 from the final production plan.',
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
