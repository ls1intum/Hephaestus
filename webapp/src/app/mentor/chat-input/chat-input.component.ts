import { Component, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { LucideAngularModule, Send } from 'lucide-angular';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { HlmSpinnerComponent } from '@spartan-ng/ui-spinner-helm';
import { HlmInputDirective } from '@spartan-ng/ui-input-helm';

@Component({
  selector: 'app-chat-input',
  templateUrl: './chat-input.component.html',
  standalone: true,
  imports: [CommonModule, HlmButtonModule, HlmSpinnerComponent, FormsModule, HlmCardModule, HlmInputDirective, LucideAngularModule]
})
export class ChatInputComponent {
  protected Send = Send;

  isClosed = input.required<boolean>();
  isSending = input.required<boolean>();
  message = signal<string>('');
  sendMessage = output<string>();

  onSendMessage() {
    if (this.isSending() || !this.message()) {
      return;
    }

    this.sendMessage.emit(this.message());
    setTimeout(() => {
      this.message.set('');
    });
  }
}
