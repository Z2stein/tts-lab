import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TextLengthService } from './text-length.service';

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

  constructor(private readonly textLengthService: TextLengthService) {}

  async submit(): Promise<void> {
    this.error = null;
    this.length = null;

    try {
      this.length = await this.textLengthService.getLength(this.textControl.value);
    } catch (e) {
      this.error = e instanceof Error ? e.message : 'Unknown error.';
    }
  }
}
