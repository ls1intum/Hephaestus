import { CommonModule } from '@angular/common';
import { Component, inject, model } from '@angular/core';
import { WorkspaceService } from '@app/core/modules/openapi/api/workspace.service';
import { SecurityStore } from '@app/core/security/security-store.service';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { HlmInputDirective } from '@spartan-ng/ui-input-helm';
import { HlmSkeletonModule } from '@spartan-ng/ui-skeleton-helm';
import { HlmScrollAreaModule } from '@spartan-ng/ui-scrollarea-helm';
import { RouterModule } from '@angular/router';
import { BrnAlertDialogContentDirective, BrnAlertDialogTriggerDirective } from '@spartan-ng/brain/alert-dialog';
import {
  HlmAlertDialogActionButtonDirective,
  HlmAlertDialogCancelButtonDirective,
  HlmAlertDialogComponent,
  HlmAlertDialogContentComponent,
  HlmAlertDialogDescriptionDirective,
  HlmAlertDialogFooterComponent,
  HlmAlertDialogHeaderComponent,
  HlmAlertDialogTitleDirective
} from '@spartan-ng/ui-alertdialog-helm';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { lucideTrash2 } from '@ng-icons/lucide';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-workspace',
  imports: [
    FormsModule,
    CommonModule,
    RouterModule,
    HlmCardModule,
    HlmInputDirective,
    HlmSkeletonModule,
    HlmScrollAreaModule,
    BrnAlertDialogTriggerDirective,
    BrnAlertDialogContentDirective,
    HlmAlertDialogComponent,
    HlmAlertDialogHeaderComponent,
    HlmAlertDialogFooterComponent,
    HlmAlertDialogTitleDirective,
    HlmAlertDialogDescriptionDirective,
    HlmAlertDialogCancelButtonDirective,
    HlmAlertDialogActionButtonDirective,
    HlmAlertDialogContentComponent,
    HlmButtonDirective,
    NgIconComponent
  ],
  providers: [provideIcons({ lucideTrash2 })],
  templateUrl: './workspace.component.html'
})
export class WorkspaceComponent {
  workspaceService = inject(WorkspaceService);
  securityStore = inject(SecurityStore);
  queryClient = inject(QueryClient);

  signedIn = this.securityStore.signedIn;
  user = this.securityStore.loadedUser;

  repositoriesToMonitorInput = model('');

  repositoriesToMonitor = injectQuery(() => ({
    enabled: this.signedIn(),
    queryKey: ['workspace', 'repositoriesToMonitor'],
    queryFn: async () => lastValueFrom(this.workspaceService.getRepositoriesToMonitor())
  }));

  addRepositoriesToMonitor = injectMutation(() => ({
    mutationFn: (nameWithOwner: string) => {
      const [owner, name] = nameWithOwner.split('/');
      return lastValueFrom(this.workspaceService.addRepositoryToMonitor(owner, name));
    },
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: ['workspace', 'repositoriesToMonitor'] });
      this.repositoriesToMonitorInput.set('');
    }
  }));

  removeRepositoriesToMonitor = injectMutation(() => ({
    mutationFn: (nameWithOwner: string) => {
      const [owner, name] = nameWithOwner.split('/');
      return lastValueFrom(this.workspaceService.removeRepositoryToMonitor(owner, name));
    },
    onSuccess: () => this.queryClient.invalidateQueries({ queryKey: ['workspace', 'repositoriesToMonitor'] })
  }));

  resetAndRecalculateLeagues = injectMutation(() => ({
    mutationFn: () => lastValueFrom(this.workspaceService.resetAndRecalculateLeagues()),
    onSuccess: () => this.queryClient.invalidateQueries({ queryKey: ['workspace', 'resetAndRecalculateLeagues'] })
  }));
}
