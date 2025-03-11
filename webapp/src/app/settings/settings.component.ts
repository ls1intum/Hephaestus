import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { lastValueFrom } from 'rxjs';
import { UserService, UserSettings } from '@app/core/modules/openapi';
import { SecurityStore } from '@app/core/security/security-store.service';
import { injectMutation, injectQuery } from '@tanstack/angular-query-experimental';
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
import { HlmLabelDirective } from '@spartan-ng/ui-label-helm';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { HlmSwitchComponent } from '@spartan-ng/ui-switch-helm';

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
    HlmButtonDirective,
    HlmLabelDirective,
    HlmSwitchComponent,
    ReactiveFormsModule
  ],
  templateUrl: './settings.component.html'
})
export class SettingsComponent {
  router = inject(Router);
  securityStore = inject(SecurityStore);
  userService = inject(UserService);
  _newNotificationEnabled = new FormControl(false);

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

  settingsQuery = injectQuery(() => ({
    queryKey: ['settings'],
    queryFn: () => lastValueFrom(this.userService.getUserSettings()),
    onSuccess: (settings: UserSettings) => {
      this._newNotificationEnabled.setValue(settings.receiveNotifications ?? false);
    }
  }));

  settingsMutation = injectMutation(() => ({
    mutationFn: (settings: UserSettings) => lastValueFrom(this.userService.updateUserSettings(settings))
  }));

  saveSettings() {
    this.settingsMutation.mutate({ receiveNotifications: this._newNotificationEnabled.value ?? false });
  }
}
