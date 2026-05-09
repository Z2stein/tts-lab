import { AnnotatedMarkup, AnnotatedSpeakerTurn } from '../models/audiobook-studio.types';

export function markupFor(turn: AnnotatedSpeakerTurn): AnnotatedMarkup {
  const tags: string[] = [];
  let remainingText = turn.text.trimStart();
  let match = /^\[([^\]]+)]\s*/.exec(remainingText);

  while (match) {
    tags.push(match[1]);
    remainingText = remainingText.slice(match[0].length).trimStart();
    match = /^\[([^\]]+)]\s*/.exec(remainingText);
  }

  return { tags, text: remainingText };
}
