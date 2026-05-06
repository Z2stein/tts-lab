import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ChatMessage } from './chat-message';
import { ChatbotService } from './chatbot.service';

@Component({
  selector: 'app-chatbot-widget',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chatbot-widget.component.html',
  styleUrl: './chatbot-widget.component.css'
})
export class ChatbotWidgetComponent {
  isOpen = false;
  input = '';
  loading = false;
  error: string | null = null;
  conversationId: string | null = null;
  messages: ChatMessage[] = [];

  constructor(private readonly chatbotService: ChatbotService) {}

  toggle(): void { this.isOpen = !this.isOpen; }

  async send(): Promise<void> {
    if (!this.input.trim() || this.loading) {
      return;
    }

    const message = this.input.trim();
    this.messages.push({ role: 'user', text: message });
    this.input = '';
    this.loading = true;
    this.error = null;

    try {
      const response = await this.chatbotService.sendMessage(message, this.conversationId);
      this.conversationId = response.conversationId;
      this.messages.push({ role: 'assistant', text: response.answer });
    } catch (error) {
      this.error = error instanceof Error ? error.message : 'Chatbot is currently unavailable.';
    } finally {
      this.loading = false;
    }
  }
}
