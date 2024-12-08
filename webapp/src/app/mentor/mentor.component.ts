import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { lastValueFrom } from 'rxjs';
import { injectMutation, injectQuery, injectQueryClient } from '@tanstack/angular-query-experimental';
import { SessionsCardComponent } from './sessions-card/sessions-card.component';
import { MessagesComponent } from './messages/messages.component';
import { ChatInputComponent } from './chat-input/chat-input.component';
import { LucideAngularModule, CircleX } from 'lucide-angular';
import { MessageService, SessionService } from '@app/core/modules/openapi';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { FirstSessionCardComponent } from './start-session-card/start-session-card.component';
import { HlmAlertModule } from '@spartan-ng/ui-alert-helm';

@Component({
  selector: 'app-mentor',
  templateUrl: './mentor.component.html',
  standalone: true,
  imports: [
    CommonModule,
    FirstSessionCardComponent,
    SessionsCardComponent,
    MessagesComponent,
    ChatInputComponent,
    HlmButtonModule,
    HlmAlertModule,
    LucideAngularModule
  ]
})
export class MentorComponent {
  protected CircleX = CircleX;

  messageService = inject(MessageService);
  sessionService = inject(SessionService);

  selectedSessionId = signal<number | undefined>(undefined);

  queryClient = injectQueryClient();

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
    }
  }));

  sendMessage = injectMutation(() => ({
    mutationFn: async ({ sessionId, message }: { sessionId: number; message: string }) => lastValueFrom(this.messageService.createMessage(sessionId, message)),

    onSettled: () => {
      this.queryClient.invalidateQueries({ queryKey: ['sessions', this.selectedSessionId()] });
    }
  }));
}
