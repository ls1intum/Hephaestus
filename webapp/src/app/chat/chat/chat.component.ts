import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { lastValueFrom } from 'rxjs';
import { injectMutation, injectQuery } from '@tanstack/angular-query-experimental';
import { SessionsCardComponent } from '../sessions-card/sessions-card.component';
import { MessagesComponent } from '../messages/messages.component';
import { InputComponent } from '../input/input.component';
import { SecurityStore } from '@app/core/security/security-store.service';
import { Message, Session } from '@app/core/modules/openapi';
import { MessageService, SessionService } from '@app/core/modules/openapi';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { HlmSpinnerComponent } from '@spartan-ng/ui-spinner-helm';
import { FirstSessionCardComponent } from '../first-session-card/first-session-card.component';

@Component({
  selector: 'app-chat',
  templateUrl: './chat.component.html',
  standalone: true,
  imports: [CommonModule, FirstSessionCardComponent, HlmSpinnerComponent, SessionsCardComponent, MessagesComponent, InputComponent, HlmButtonModule]
})
export class ChatComponent {
  securityStore = inject(SecurityStore);
  messageService = inject(MessageService);
  sessionService = inject(SessionService);

  signedIn = this.securityStore.signedIn;
  user = this.securityStore.loadedUser;

  messageHistory = signal<Message[]>([]);
  selectedSession = signal<Session | null>(null);
  sessions = signal<Session[]>([]);
  isLoading = signal(true);

  latestMessageContent = '';

  protected query_sessions = injectQuery(() => ({
    enabled: this.signedIn(),
    queryKey: ['sessions', { login: this.user()?.username }],
    queryFn: async () => {
      const username = this.user()?.username;
      if (!username) {
        throw new Error('User is not logged in or username is undefined.');
      }
      const sessions = await lastValueFrom(this.sessionService.getSessions(username));
      if (sessions.length > 0 && this.selectedSession() == null) {
        this.selectedSession.set(sessions.slice(-1)[0]);
      }
      this.sessions.set(sessions);
      this.isLoading.set(false);
      return sessions;
    }
  }));

  handleSessionSelect(sessionId: number): void {
    const session = this.sessions().find((s) => s.id === sessionId);
    if (session) {
      this.selectedSession.set(session);
      this.query_sessions.refetch();
    }
  }

  handleCreateSession(): void {
    this.createSession.mutate();
  }

  protected createSession = injectMutation(() => ({
    mutationFn: async () => {
      const username = this.user()?.username;
      if (!username) {
        throw new Error('User is not logged in or username is undefined.');
      }
      await lastValueFrom(this.sessionService.createSession(username));
    },
    onSuccess: () => {
      this.query_sessions.refetch();
    }
  }));

  protected query_messages = injectQuery(() => ({
    enabled: !!this.selectedSession,
    queryKey: ['messages', { sessionId: this.selectedSession()?.id }],
    queryFn: async () => {
      const selectedSessionId = this.selectedSession()?.id;
      if (selectedSessionId == null) {
        throw new Error('No session selected!');
      }
      const loadedMessages = await lastValueFrom(this.messageService.getMessages(selectedSessionId));
      this.messageHistory.set(loadedMessages);
      return loadedMessages;
    }
  }));

  protected sendMessage = injectMutation(() => ({
    queryKey: ['messages', 'create'],
    mutationFn: async ({ sessionId }: { sessionId: number }) => {
      if (!this.selectedSession) {
        throw new Error('No session selected!');
      }
      await lastValueFrom(this.messageService.createMessage(sessionId, this.latestMessageContent));
    },
    onSuccess: () => {
      this.query_messages.refetch();
    }
  }));

  handleSendMessage(content: string): void {
    if (!this.selectedSession) {
      console.error('No session selected!');
      return;
    }

    const selectedSessionId = this.selectedSession()?.id;
    if (selectedSessionId == null) {
      console.error('No session selected!');
      return;
    } else {
      // show the user message directly after sending
      const userMessage: Message = {
        id: Math.random(), // temporary id until the message is sent
        sessionId: selectedSessionId,
        sender: 'USER',
        content: content,
        sentAt: new Date().toISOString()
      };

      this.messageHistory.set([...this.messageHistory(), userMessage]);

      this.latestMessageContent = content;
      this.sendMessage.mutate({ sessionId: selectedSessionId });
    }
  }
}
