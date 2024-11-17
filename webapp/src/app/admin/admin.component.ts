import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { AdminService } from '@app/core/modules/openapi/api/admin.service';
import { SecurityStore } from '@app/core/security/security-store.service';
import { injectMutation, injectQuery, injectQueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { HlmInputDirective } from '@spartan-ng/ui-input-helm';
import { HlmSkeletonModule } from '../../libs/ui/ui-skeleton-helm/src/index';
import { HlmScrollAreaModule } from '@spartan-ng/ui-scrollarea-helm';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { RouterModule, } from '@angular/router';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [CommonModule, RouterModule, HlmCardModule, HlmInputDirective, HlmSkeletonModule, HlmScrollAreaModule, ReactiveFormsModule, HlmButtonModule],
  templateUrl: './admin.component.html'
})
export class AdminComponent {
  adminService = inject(AdminService);
  securityStore = inject(SecurityStore);
  queryClient = injectQueryClient();

  signedIn = this.securityStore.signedIn;
  user = this.securityStore.loadedUser;

  configQuery = injectQuery(() => ({
    enabled: this.signedIn(),
    queryKey: ['admin', 'config'],
    queryFn: async () => {
      const adminConfig = await lastValueFrom(this.adminService.getConfig());
      this.repositoriesForm.setValue(this.convertJSON(adminConfig.repositoriesToMonitor));
      return adminConfig;
    },
    select: (data) => data.repositoriesToMonitor
  }));

  repositoriesForm = new FormControl(this.configQuery.data() ? this.convertJSON(this.configQuery.data()!) : '[]');

  updateRepositories = injectMutation(() => ({
    mutationFn: (repos: string) => lastValueFrom(this.adminService.updateRepositories(JSON.parse(repos))),
    queryKey: ['admin', 'repository', 'update'],
    onSettled: () => this.queryClient.invalidateQueries({ queryKey: ['admin', 'config'] })
  }));

  convertJSON(value: Set<string>) {
    return JSON.stringify(value, null, 4);
  }
}

