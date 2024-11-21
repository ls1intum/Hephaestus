import { Component, inject, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MessagesComponent } from '../messages/messages.component';
import { InputComponent } from '../input/input.component';
import { SecurityStore } from '@app/core/security/security-store.service';
import { Message } from '@app/core/modules/openapi';


import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { Plus, LucideAngularModule } from 'lucide-angular';

@Component({
  selector: 'app-chat',
  templateUrl: './chat.component.html',
  standalone: true,
  imports: [CommonModule, MessagesComponent, InputComponent, HlmButtonModule, LucideAngularModule],
})
export class ChatComponent {
  protected Plus = Plus;
  securityStore = inject(SecurityStore);
  signedIn = this.securityStore.signedIn;
  user = this.securityStore.loadedUser;

  session = input<Message>();

  previousSessions = [
    { id: 1, name: 'Session 1' },
    { id: 2, name: 'Session 2' },
    { id: 3, name: 'Session 3' },
  ];

  messages: { sender: Message.SenderEnum; content: string; timestamp: string }[] = []; // Mock messages array

  // Mock message handler
  sendMessage(content: string): void {
    // Add the user's message to the mocked messages array
    this.messages.push({
      sender: Message.SenderEnum.User,
      content: content,
      timestamp: new Date().toISOString(),
    });
  }

  startSession() {
    console.log('Starting Reflective Session...');
    // Logic to start a new reflective session
  }

  fetchSessions(session: { id: number; name: string }) {
    console.log('Fetching session:', session.name);
    // Logic to fetch the selected session
  }
}
