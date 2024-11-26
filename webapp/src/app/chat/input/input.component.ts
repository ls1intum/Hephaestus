import { Component, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';

import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { LucideAngularModule, Send } from 'lucide-angular';

@Component({
  selector: 'app-chat-input',
  templateUrl: './input.component.html',
  standalone: true,
  imports: [CommonModule, HlmButtonModule, FormsModule, HlmCardModule, LucideAngularModule]
})
export class InputComponent {
  protected Send = Send;

  messageSent = output<string>();
  messageText = '';

  onSend() {
    if (this.messageText.trim() !== '') {
      this.messageSent.emit(this.messageText);
      this.messageText = '';
    }
  }
}
