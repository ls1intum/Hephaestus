import { Component, effect, inject, computed, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { lastValueFrom } from 'rxjs';
import { injectMutation, injectQuery, injectQueryClient } from '@tanstack/angular-query-experimental';
import { SessionsCardComponent } from './sessions-card/sessions-card.component';
import { MessagesComponent } from './messages/messages.component';
import { ChatInputComponent } from './chat-input/chat-input.component';
import { LucideAngularModule, CircleX } from 'lucide-angular';
import { Message, MessageService, SessionService } from '@app/core/modules/openapi';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { StartSessionCardComponent } from './start-session-card/start-session-card.component';
import { HlmAlertModule } from '@spartan-ng/ui-alert-helm';
import { HlmScrollAreaComponent } from '@spartan-ng/ui-scrollarea-helm';

@Component({
  selector: 'app-mentor',
  templateUrl: './mentor.component.html',
  imports: [
    CommonModule,
    StartSessionCardComponent,
    SessionsCardComponent,
    MessagesComponent,
    ChatInputComponent,
    HlmButtonModule,
    HlmAlertModule,
    HlmScrollAreaComponent,
    LucideAngularModule
  ]
})
export class MentorComponent {
  protected CircleX = CircleX;

  messageService = inject(MessageService);
  sessionService = inject(SessionService);

  selectedSessionId = signal<number | undefined>(undefined);
  messagesScrollArea = viewChild(HlmScrollAreaComponent);

  queryClient = injectQueryClient();

  constructor() {
    effect(() => {
      this.selectedSessionMessages.data(); // captures the dependency
      setTimeout(() => this.scrollToBottom(), 0);
    });
  }

  selectedSessionClosed = computed(() => {
    const selectedId = this.selectedSessionId();
    if (!selectedId) return false;

    const sessions = this.sessions.data();
    if (!sessions) return false;

    const selectedSession = sessions.find((session) => session.id === selectedId);
    return selectedSession?.isClosed ?? false;
  });

  sessions = injectQuery(() => ({
    queryKey: ['sessions'],
    queryFn: async () => (await lastValueFrom(this.sessionService.getAllSessions())).reverse()
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
    onSuccess: (session) => {
      this.selectedSessionId.set(session.id);
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
    onError: (err, newTodo, context) => {
      this.queryClient.setQueryData(['sessions', this.selectedSessionId()], context?.previousMessages);
    },
    onSettled: () => {
      this.queryClient.invalidateQueries({ queryKey: ['sessions', this.selectedSessionId()] });
    }
  }));

  scrollToBottom() {
    this.messagesScrollArea()?.scrollbar().scrollTo({ bottom: 0, duration: 300 });
  }
}
