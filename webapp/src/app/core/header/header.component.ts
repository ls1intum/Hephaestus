import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SecurityStore } from '@app/core/security/security-store.service';
import { AdminService } from '../modules/openapi';
import { injectQuery, injectQueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  standalone: true,
  imports: [RouterLink]
})
export class HeaderComponent {
  readonly securityStore = inject(SecurityStore);
  protected readonly user = this.securityStore.loadedUser;

  adminService = inject(AdminService);
  JSON = JSON;

  query = injectQuery(() => ({
    queryKey: ['me'],
    queryFn: async () => lastValueFrom(this.adminService.getGretting())
  }));

  queryClient = injectQueryClient();

  protected signOut() {
    this.securityStore.signOut();
    this.queryClient.invalidateQueries({ queryKey: ['me'] });
  }

  protected signIn() {
    this.securityStore.signIn();
    this.queryClient.invalidateQueries({ queryKey: ['me'] });
  }
}
