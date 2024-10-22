import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { AdminService } from '@app/core/modules/openapi/api/admin.service';
import { SecurityStore } from '@app/core/security/security-store.service';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { HlmInputDirective } from '@spartan-ng/ui-input-helm';
import { HlmSkeletonModule } from '../../libs/ui/ui-skeleton-helm/src/index';
import { HlmScrollAreaModule } from '@spartan-ng/ui-scrollarea-helm';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { RouterModule, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [CommonModule, RouterModule, HlmCardModule, HlmInputDirective, HlmSkeletonModule, HlmScrollAreaModule, ReactiveFormsModule, HlmButtonModule, RouterOutlet],
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

  configQuery = injectQuery(() => ({
    enabled: this.signedIn(),
    queryKey: ['admin', 'config'],
    queryFn: async () => {
      const adminConfig = await lastValueFrom(this.adminService.getConfig());
      this.repositoriesForm.setValue(JSON.stringify(adminConfig.repositoriesToMonitor));
      return adminConfig;
    }
  }));

  repositoriesForm = new FormControl('');

  saveRepositories() {
    const repositories = JSON.parse(this.repositoriesForm.value ?? '[]') as string[];
    console.log('Saving repositories', repositories);
    this.adminService.updateRepositories(repositories).subscribe({
      next: () => this.configQuery.refetch(),
      error: () => console.error('Error saving repositories'),
      complete: () => console.log('Repositories saved')
    });
  }
}
