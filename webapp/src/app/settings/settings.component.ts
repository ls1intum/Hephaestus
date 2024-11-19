import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { UserService } from '@app/core/modules/openapi';
import { SecurityStore } from '@app/core/security/security-store.service';
import { injectMutation } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [],
  template: `<button (click)="deleteUser()">Delete User</button>`
})
export class SettingsComponent {
  router = inject(Router);
  securityStore = inject(SecurityStore);
  userService = inject(UserService);

  mutation = injectMutation(() => ({
    mutationFn: () => lastValueFrom(this.userService.deleteUser()),
    onSuccess: () => {
      this.securityStore.signOut();
      this.router.navigate(['/']);
    }
  }));

  deleteUser() {
    this.mutation.mutate();
  }
}
