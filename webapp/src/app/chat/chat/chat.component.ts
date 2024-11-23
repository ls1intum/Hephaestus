import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { lastValueFrom } from 'rxjs';
import { injectMutation, injectQuery } from '@tanstack/angular-query-experimental';
import { SessionsCardComponent } from '../sessions-card/sessions-card.component';
import { MessagesComponent } from '../messages/messages.component';
import { InputComponent } from '../input/input.component';
import { SecurityStore } from '@app/core/security/security-store.service';
import { Message, Session } from '@app/core/modules/openapi';
import { MessageService } from '@app/core/modules/openapi'; // Ensure the MessageService is imported

import { HlmButtonModule } from '@spartan-ng/ui-button-helm';

@Component({
  selector: 'app-chat',
  templateUrl: './chat.component.html',
  standalone: true,
  imports: [CommonModule, SessionsCardComponent, MessagesComponent, InputComponent, HlmButtonModule],
})
export class ChatComponent {
  securityStore = inject(SecurityStore);
  messageService = inject(MessageService);

  signedIn = this.securityStore.signedIn;
  user = this.securityStore.loadedUser;

  content = '';

  messageHistory = signal<Message[]>([]);
  selectedSession: Session | null = null;

  handleSessionSelect(session: Session): void {
    this.selectedSession = session;
    this.query.refetch();
  }

  protected query = injectQuery(() => ({
    enabled: !!this.selectedSession,
    queryKey: ['messages', { sessionId: this.selectedSession?.id }],
    queryFn: async () => {
      if (!this.selectedSession) {
        throw new Error('No session selected!');
      }
      const loadedMessages = await lastValueFrom(this.messageService.getMessages(this.selectedSession.id));
      this.messageHistory.set(loadedMessages);
      return loadedMessages;
    },
  }));

  protected sendMessage = injectMutation(() => ({
    queryKey: ['messages', 'create'],
    mutationFn: ({sessionId} : { sessionId: number}) => {
      if (!this.selectedSession) {
        throw new Error('No session selected!');
      }
      return lastValueFrom(this.messageService.createMessage(sessionId, this.content)); 
    },
    onSuccess: () => {
      this.query.refetch();
    }
  }));
  

  handleSendMessage(content: string): void {
    if (!this.selectedSession) {
      console.error('No session selected!');
      return;
    }
    this.content = content;
    this.sendMessage.mutate({sessionId: this.selectedSession.id});
  }

}
