import { Component, inject } from '@angular/core';
import { UserService } from '@app/core/modules/openapi';
import { injectMutation } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [],
  template: `<button (click)="deleteUser()">Delete User</button>`
})
export class SettingsComponent {
  userService = inject(UserService);

  mutation = injectMutation(() => ({
    mutationFn: () => lastValueFrom(this.userService.deleteUser())
  }));

  deleteUser() {
    this.mutation.mutate();
  }
}
