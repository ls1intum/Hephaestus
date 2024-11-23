import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { lastValueFrom } from 'rxjs';
import { injectMutation, injectQuery } from '@tanstack/angular-query-experimental';
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

  // mock data
  messages: Message[] = [];

  securityStore = inject(SecurityStore);
  messageService = inject(MessageService);

  signedIn = this.securityStore.signedIn;
  user = this.securityStore.loadedUser;

  selectedSession: Session | null = null;

  handleSessionSelect(session: Session): void {
    this.selectedSession = session;
    this.query.refetch();
  }

  protected query = injectQuery(() => ({
    enabled: this.signedIn(),
    queryKey: ['messages', { sessionId: this.selectedSession?.id }],
    queryFn: async () => {
      if (!this.selectedSession) {
        throw new Error('No session selected!');
      }
      const messageHistory = await lastValueFrom(this.messageService.getMessages(this.selectedSession?.id));
      this.messages = messageHistory;
      return messageHistory;
    },
  }));


  protected sendMessage = injectMutation(() => ({
    mutationKey: ['messages', { sessionId: this.selectedSession?.id }],
    mutationFn: async (content: string) => {
      if (!this.selectedSession) {
        throw new Error('No session selected!');
      }
      await lastValueFrom(this.messageService.createMessage(this.selectedSession.id, content));
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
    this.sendMessage.mutate(content);
    

    // this.messageService.createMessage(this.selectedSession.id, content).subscribe(
    //   (savedMessage: Message) => {
    //     this.messages.push(savedMessage); // Append the new message to the array
    //     console.log('Message sent:', savedMessage);
    //   });


    this.query.refetch();
  }

}
