export function formatSpeakerDisplayName(speakerName: string): string {
  return speakerName
    .replace(/[_-]+/g, ' ')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .trim()
    .replace(/\s+/g, ' ')
    .replace(/\b\w/g, (character) => character.toUpperCase());
}

export function speakerInitials(speakerName: string): string {
  return formatSpeakerDisplayName(speakerName)
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join('')
    .toUpperCase();
}

export function normalizedSpeakerKey(value: string): string {
  return formatSpeakerDisplayName(value).toLowerCase();
}
