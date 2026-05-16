import { Pipe, PipeTransform } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

@Pipe({
  name: 'highlightTags',
  standalone: true,
})
export class HighlightTagsPipe implements PipeTransform {
  constructor(private sanitizer: DomSanitizer) {}

  transform(text: string): SafeHtml {
    if (!text) {
      return text;
    }

    const highlighted = text.replace(
      /\[([^\]]+)]/g,
      '<span class="emotion-badge">[$1]</span>'
    );

    return this.sanitizer.bypassSecurityTrustHtml(highlighted);
  }
}
