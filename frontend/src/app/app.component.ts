import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  textControl = new FormControl('', { nonNullable: true });
  length: number | null = null;
  error: string | null = null;

  async submit(): Promise<void> {
    this.error = null;
    this.length = null;

    try {
      const response = await fetch('/api/text-length', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ text: this.textControl.value })
      });

      if (!response.ok) {
        throw new Error('Backend request failed.');
      }

      const data = (await response.json()) as { length: number };
      this.length = data.length;
    } catch (e) {
      this.error = e instanceof Error ? e.message : 'Unknown error.';
    }
  }
}
