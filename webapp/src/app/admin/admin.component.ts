import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { AdminService } from '@app/core/modules/openapi/api/admin.service';
import { SecurityStore } from '@app/core/security/security-store.service';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './admin.component.html'
})
export class AdminComponent {
  adminService = inject(AdminService);
  securityStore = inject(SecurityStore);

  signedIn = this.securityStore.signedIn;
  user = this.securityStore.loadedUser;

  query = injectQuery(() => ({
    enabled: this.signedIn(),
    queryKey: ['admin', 'greeting'],
    queryFn: async () => lastValueFrom(this.adminService.getGretting())
  }));
}
