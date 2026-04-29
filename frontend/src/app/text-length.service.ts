import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class TextLengthService {
  async getLength(text: string): Promise<number> {
    const response = await fetch('/api/text-length', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ text })
    });

    if (!response.ok) {
      throw new Error('Backend request failed.');
    }

    const data = (await response.json()) as { length: number };
    return data.length;
  }
}
