import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SessionsCardComponent } from '../sessions-card/sessions-card.component';
import { MessagesComponent } from '../messages/messages.component';
import { InputComponent } from '../input/input.component';
import { SecurityStore } from '@app/core/security/security-store.service';
import { Message, Session } from '@app/core/modules/openapi';
import { SessionService, MessageService } from '@app/core/modules/openapi'; // Ensure the MessageService is imported

import { HlmButtonModule } from '@spartan-ng/ui-button-helm';

@Component({
  selector: 'app-chat',
  templateUrl: './chat.component.html',
  standalone: true,
  imports: [CommonModule, SessionsCardComponent, MessagesComponent, InputComponent, HlmButtonModule],
})
export class ChatComponent {
  securityStore = inject(SecurityStore);
  sessionService = inject(SessionService);
  messageService = inject(MessageService); // Inject MessageService
  signedIn = this.securityStore.signedIn;
  user = this.securityStore.loadedUser;

  selectedSession?: Session; // Track the selected session
  messages: Message[] = []; // Messages for the selected session

  // Handle session selection
  onSessionSelected(session: Session): void {
    this.selectedSession = session;
    console.log('Selected session:', session);
    this.loadMessagesForSession(session.id); // Fetch messages for the selected session
  }

  // Handle session creation
  onSessionCreated(): void {
    console.log('New session created!');
    // Refresh the session list or take other actions if needed
  }

  // Fetch messages for a specific session
  loadMessagesForSession(sessionId: number): void {
    this.messageService.getMessages(sessionId).subscribe(
      (data: Message[]) => {
        this.messages = data; // Update the messages array
        console.log('Loaded messages:', data);
      }
    );
  }

  // Send a message
  sendMessage(content: string): void {
    if (!this.selectedSession) {
      console.error('No session selected!');
      return;
    }

    this.messageService.createMessage(this.selectedSession.id, content).subscribe(
      (savedMessage: Message) => {
        this.messages.push(savedMessage); // Append the new message to the array
        console.log('Message sent:', savedMessage);
      });
  }
}
