import { Component, input, model, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { lucidePlus } from '@ng-icons/lucide';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { BrnToggleDirective } from '@spartan-ng/brain/toggle';
import { Session } from '@app/core/modules/openapi';
import { HlmToggleDirective } from '@spartan-ng/ui-toggle-helm';
import { HlmCardDirective } from '@spartan-ng/ui-card-helm';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';
import { BrnAlertDialogModule } from '@spartan-ng/brain/alert-dialog';
import { BrnAlertDialogContentDirective, BrnAlertDialogTriggerDirective } from '@spartan-ng/brain/alert-dialog';
import {
  HlmAlertDialogActionButtonDirective,
  HlmAlertDialogCancelButtonDirective,
  HlmAlertDialogComponent,
  HlmAlertDialogContentComponent,
  HlmAlertDialogDescriptionDirective,
  HlmAlertDialogFooterComponent,
  HlmAlertDialogHeaderComponent,
  HlmAlertDialogTitleDirective,
} from '@spartan-ng/ui-alertdialog-helm';


@Component({
  selector: 'app-sessions-card',
  templateUrl: './sessions-card.component.html',
  imports: [
    CommonModule,
    BrnToggleDirective,
    NgIconComponent,
    HlmSkeletonComponent,
    BrnAlertDialogModule,
    HlmAlertDialogDescriptionDirective,
    HlmAlertDialogTitleDirective,
    HlmToggleDirective,
    HlmButtonModule,
    HlmCardDirective,
    HlmAlertDialogActionButtonDirective,
    HlmAlertDialogCancelButtonDirective,
    HlmAlertDialogComponent,
    HlmAlertDialogContentComponent,
    BrnAlertDialogContentDirective,
    HlmAlertDialogFooterComponent,
    HlmAlertDialogHeaderComponent,
    BrnAlertDialogTriggerDirective,
  ],
  providers: [provideIcons({ lucidePlus })]
})
export class SessionsCardComponent {
  sessions = input<Session[]>();
  selectedSessionId = model<number | null>();
  createNewSession = output<void>();
  isLoading = input<boolean>();
  isLastSessionClosed = input<boolean>();
}
