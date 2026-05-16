export interface ParsedPerformanceDirections {
  emotionTags: string[];
  speakerName?: string;
  originalText?: string;
}

export function parsePerformanceDirections(performanceDirections?: string | null): ParsedPerformanceDirections {
  if (!performanceDirections) {
    return { emotionTags: [] };
  }

  const emotionTags: string[] = [];
  let remaining = performanceDirections;

  // Extract leading tags: [tag1] [tag2] [tag3] ...
  while (remaining.startsWith('[')) {
    const closeIndex = remaining.indexOf(']');
    if (closeIndex === -1) break;
    const tag = remaining.slice(1, closeIndex);
    emotionTags.push(tag);
    remaining = remaining.slice(closeIndex + 1).trim();
  }

  // Extract speaker name (typically first word before comma)
  let speakerName: string | undefined;
  let originalText: string | undefined;

  const speakerMatch = remaining.match(/^([^,[]+),\s*/);
  if (speakerMatch) {
    speakerName = speakerMatch[1].trim();
    originalText = remaining.slice(speakerMatch[0].length);
  } else {
    // No comma found, try to extract first word as speaker if text follows
    const wordMatch = remaining.match(/^(\S+)\s+/);
    if (wordMatch && !wordMatch[1].startsWith('[')) {
      speakerName = wordMatch[1];
      originalText = remaining.slice(wordMatch[0].length).trim();
    } else {
      // No clear speaker name pattern, treat entire text as original
      originalText = remaining;
    }
  }

  return {
    emotionTags,
    speakerName,
    originalText: originalText ? originalText.trim() : undefined,
  };
}
