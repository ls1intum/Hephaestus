import { Component, computed, inject, signal, booleanAttribute, Signal, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { lastValueFrom } from 'rxjs';
import { SecurityStore } from '@app/core/security/security-store.service';
import { Plus, LucideAngularModule } from 'lucide-angular';
import { SessionService, Session } from '@app/core/modules/openapi';
import { injectMutation, injectQuery } from '@tanstack/angular-query-experimental';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';

@Component({
  standalone: true,
  selector: 'app-sessions-card',
  templateUrl: './sessions-card.component.html',
  imports: [CommonModule, LucideAngularModule, HlmButtonModule]
})
export class SessionsCardComponent {
  protected Plus = Plus;

  isLoading = signal(false);

  securityStore = inject(SecurityStore);
  sessionService = inject(SessionService);

  signedIn = this.securityStore.signedIn;
  user = this.securityStore.loadedUser;

  query = injectQuery(() => ({
    enabled: this.signedIn(),
    queryKey: ['sessions', { login: this.user()?.username }],
    queryFn: async () => {
      const username = this.user()?.username;
      if (!username) {
        throw new Error('User is not logged in or username is undefined.');
      }
      const sessions = await lastValueFrom(this.sessionService.getSessions(username));
      this.activeSessionId.set(sessions.slice(-1)[0]?.id);
      return sessions;
    },
  }));

  sessions: Signal<Session[]> = computed(() => (this.query.data() ?? []).slice().reverse());
  activeSessionId = signal<number | null>(null);

  handleCreateSession(): void {
    this.createSession.mutate();
  }

  handleSessionSelect(sessionId: number): void {
    this.activeSessionId.set(sessionId);
  }

  protected createSession = injectMutation(() => ({
    mutationKey: ['sessions', { login: this.user()?.username }],
    mutationFn: async () => {
      const username = this.user()?.username;
      if (!username) {
        throw new Error('User is not logged in or username is undefined.');
      }
      await lastValueFrom(this.sessionService.createSession(username));
    },
    onSuccess: () => {
      this.query.refetch();
      const latestSession = this.sessions()?.slice(-1)[0];
      if (latestSession) {
        this.activeSessionId.set(latestSession.id);
      }
    }
  }));
}
