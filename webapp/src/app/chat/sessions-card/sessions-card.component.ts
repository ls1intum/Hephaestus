import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SecurityStore } from '@app/core/security/security-store.service';
import { Plus, LucideAngularModule } from 'lucide-angular';
import { ActivatedRoute } from '@angular/router';
import { KeycloakService } from '@app/core/security/keycloak.service';
import { SessionService, UserService, UserInfo } from '@app/core/modules/openapi';
import { injectMutation, injectQuery } from '@tanstack/angular-query-experimental';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';

@Component({
  standalone: true,
  selector: 'app-sessions-card',
  templateUrl: './sessions-card.component.html',
  imports: [CommonModule, LucideAngularModule, HlmButtonModule],
})
export class SessionsCardComponent {
  protected Plus = Plus;

  isLoading = signal(false);

  securityStore = inject(SecurityStore);
  sessionService = inject(SessionService);
  signedIn = this.securityStore.signedIn;
  user = this.securityStore.loadedUser;

  constructor() {
    console.log('User:', this.user(),
      'Signed in:', this.signedIn());
  }

  query = injectQuery(() => ({
    enabled: this.signedIn(),
    queryKey: ['sessions', { userId: (this.user()?.id) }],
    queryFn: async () => ([this.sessionService.getSessions])
  }));

  sessions = computed(() => this.query.data() ?? []);


  // // Signals
  // isLoading = signal(false);
  // user = signal<UserInfo | null>(null);

  // // Get the current user from the SecurityStore
  // private loadedUser = computed(() => this.securityStore.loadedUser());

  // // Fetch sessions using Angular Query
  // protected sessionsQuery = injectQuery(() => ({
  //   queryKey: ['sessions', this.loadedUser()?.id],
  //   queryFn: async () => this.sessionService.getSessions(Number(this.loadedUser()?.id)),
  //   enabled: !!this.loadedUser()?.id // Only fetch if user ID is available
  // }));

  // // Computed property to get sessions
  // protected sessions = computed(() => this.sessionsQuery.data() ?? []);

  // // Create a new session
  // protected createSession = injectMutation(() => ({
  //   mutationFn: async () => this.sessionService.createSession(Number(this.loadedUser()?.id)),
  //   onSuccess: () => {
  //     this.sessionsQuery.refetch(); // Refetch sessions after creating a new one
  //   }
  // }));

  // // Handle new session creation
  // handleCreateSession(): void {
  //   this.createSession.mutate();
  // }

  

}
