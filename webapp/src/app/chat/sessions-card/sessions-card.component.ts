import { Component, computed, inject, signal, Signal } from '@angular/core';
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

  constructor() {
    console.log('User:', this.user(), 'Signed in:', this.signedIn());
  }

  query = injectQuery(() => ({
    enabled: this.signedIn(),
    queryKey: ['sessions', { login: this.user()?.username }],
    queryFn: async () => {
      const username = this.user()?.username;
      if (!username) {
        console.log('User is not logged in or username is undefined.');
        throw new Error('User is not logged in or username is undefined.');
      }
      return lastValueFrom(this.sessionService.getSessions(username));
    }
  }));

  sessions: Signal<Session[]> = computed(() => this.query.data() ?? []);

  // // Computed property to get sessions
  // protected sessions = computed(() => this.sessionsQuery.data() ?? []);

  handleCreateSession(): void {
    this.createSession.mutate();
  }

  protected createSession = injectMutation(() => ({
    mutationFn: async () => {
      const username = this.user()?.username;
      if (!username) {
        console.log('User is not logged in or username is undefined.');
        throw new Error('User is not logged in or username is undefined.');
      }
      await lastValueFrom(this.sessionService.createSession(username));
    },
    onSuccess: () => {
      console.log('New session created');
      this.query.refetch(); // Refetch sessions after creating a new one
    }
  }));


}
