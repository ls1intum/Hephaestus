import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { lastValueFrom } from 'rxjs';
import { UserService } from '@app/core/modules/openapi';
import { SecurityStore } from '@app/core/security/security-store.service';
import { injectMutation } from '@tanstack/angular-query-experimental';
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

@Component({
  selector: 'app-settings',
  imports: [
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
    HlmButtonDirective
  ],
  template: `
    <div class="flex flex-col gap-4">
      <h1 class="text-3xl font-bold">Settings</h1>
      <hlm-alert-dialog>
        <button id="edit-profile" variant="outline" brnAlertDialogTrigger hlmBtn>Delete Account</button>
        <hlm-alert-dialog-content *brnAlertDialogContent="let ctx">
          <hlm-alert-dialog-header>
            <h3 hlmAlertDialogTitle>Are you absolutely sure?</h3>
            <p hlmAlertDialogDescription>This action cannot be undone. This will permanently delete your account and remove your data from our servers.</p>
          </hlm-alert-dialog-header>
          <hlm-alert-dialog-footer>
            <button hlmAlertDialogCancel (click)="ctx.close()">Cancel</button>
            <button hlmAlertDialogAction (click)="deleteAccount()">Delete account</button>
          </hlm-alert-dialog-footer>
        </hlm-alert-dialog-content>
      </hlm-alert-dialog>
    </div>
  `
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

  deleteAccount() {
    this.mutation.mutate();
  }
}
