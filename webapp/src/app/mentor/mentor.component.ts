import { Component, effect, inject, computed, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { lastValueFrom } from 'rxjs';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { SessionsCardComponent } from './sessions-card/sessions-card.component';
import { MessagesComponent } from './messages/messages.component';
import { ChatInputComponent } from './chat-input/chat-input.component';
import { Message, MessageService, SessionService } from '@app/core/modules/openapi';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { StartSessionCardComponent } from './start-session-card/start-session-card.component';
import { HlmAlertModule } from '@spartan-ng/ui-alert-helm';
import { toast } from 'ngx-sonner';
import { NgScrollbar, NgScrollbarModule } from 'ngx-scrollbar';
import { HlmScrollAreaDirective } from '@spartan-ng/ui-scrollarea-helm';

@Component({
  selector: 'app-mentor',
  templateUrl: './mentor.component.html',
  imports: [
    CommonModule,
    NgScrollbarModule,
    StartSessionCardComponent,
    SessionsCardComponent,
    MessagesComponent,
    ChatInputComponent,
    HlmButtonModule,
    HlmAlertModule,
    NgScrollbarModule,
    HlmScrollAreaDirective
  ]
})
export class MentorComponent {
  messageService = inject(MessageService);
  sessionService = inject(SessionService);

  selectedSessionId = signal<number | undefined>(undefined);
  lastSessionClosed = signal<boolean>(true);
  messagesScrollBar = viewChild(NgScrollbar);

  queryClient = inject(QueryClient);

  constructor() {
    effect(() => {
      this.selectedSessionMessages.data(); // captures the dependency
      setTimeout(() => this.scrollToBottom(), 0);
    });
  }

  showToast() {
    toast('Something went wrong...', { description: 'There was an error trying to generate response to your last message. If this issue persists, please contact the AET Team.' });
  }

  selectedSessionClosed = computed(() => {
    if (!this.selectedSessionId() || !this.sessions.data()?.length) return false;
  
    const selectedSession = this.sessions.data()?.find((session) => session.id === this.selectedSessionId());
    return selectedSession?.isClosed ?? false;
  });
  

  sessions = injectQuery(() => ({
    queryKey: ['sessions'],
    queryFn: async () => {
      const sessions = await lastValueFrom(this.sessionService.getAllSessions());
      const lastSession = await lastValueFrom(this.sessionService.getLastSession());
      if (lastSession) {
        this.lastSessionClosed.set(lastSession.isClosed);
      }
      return sessions;
    }
  }));

  selectedSessionMessages = injectQuery(() => ({
    enabled: !!this.selectedSessionId(),
    queryKey: ['sessions', this.selectedSessionId()],
    queryFn: async () => lastValueFrom(this.messageService.getMessages(this.selectedSessionId()!))
  }));

  createNewSession = injectMutation(() => ({
    mutationFn: async () => lastValueFrom(this.sessionService.createNewSession()),
    onSettled: async () => {
      return await this.queryClient.invalidateQueries({ queryKey: ['sessions'] });
    },
    onSuccess: async (session) => {
      if (!session) {
        this.showToast();
        return;
      }
      this.lastSessionClosed.set(session.isClosed);
      this.selectedSessionId.set(session.id);
    
      await this.queryClient.invalidateQueries({ queryKey: ['sessions'] }); 
      this.queryClient.invalidateQueries({ queryKey: ['sessions', this.selectedSessionId()] });
    }
  }));

  sendMessage = injectMutation(() => ({
    mutationFn: async ({ sessionId, message }: { sessionId: number; message: string }) => await lastValueFrom(this.messageService.createMessage(sessionId, message)),
    onMutate: async ({ message }) => {
      // Do optimistic update
      await this.queryClient.cancelQueries({ queryKey: ['sessions', this.selectedSessionId()] });
      const previousMessages = this.queryClient.getQueryData(['sessions', this.selectedSessionId()]) as Message[];

      const newMessage: Message = { id: -1, sender: 'USER', content: message, sessionId: this.selectedSessionId()!, sentAt: new Date().toISOString() };
      this.queryClient.setQueryData(['sessions', this.selectedSessionId()], (old: Message[]) => [...old, newMessage]);

      return { previousMessages };
    },
    onSuccess: (message) => {
      if (message === undefined || message === null) {
        this.showToast();
        return;
      }
      // if the last session is closed, do not show the alert dialog when creating a new session
      this.sessionService.getLastSession().subscribe((session) => {
        this.lastSessionClosed.set(session.isClosed);
      });
    },
    onError: (err, newTodo, context) => {
      this.queryClient.setQueryData(['sessions', this.selectedSessionId()], context?.previousMessages);
      this.showToast();
    },
    onSettled: () => {
      this.queryClient.invalidateQueries({ queryKey: ['sessions', this.selectedSessionId()] });
    }
  }));

  scrollToBottom() {
    this.messagesScrollBar()?.scrollTo({ bottom: 0, duration: 300 });
  }
}
