import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TextLengthService } from './text-length.service';

@Component({
  selector: 'app-text-length-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './text-length-page.component.html',
  styleUrl: './text-length-page.component.css'
})
export class TextLengthPageComponent {
  textControl = new FormControl('', { nonNullable: true });
  length: number | null = null;
  error: string | null = null;

  constructor(private readonly textLengthService: TextLengthService) {}

  async submit(): Promise<void> {
    this.error = null;
    this.length = null;

    try {
      this.length = await this.textLengthService.getLength(this.textControl.value);
    } catch (e) {
      this.error = 'Backend request failed.';
      console.error('[text-length-page] Text length request failed', e);
    }
  }
}
