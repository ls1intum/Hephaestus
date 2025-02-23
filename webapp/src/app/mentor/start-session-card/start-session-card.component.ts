import { Component, input, output } from '@angular/core';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { lucidePlus, lucideBotMessageSquare } from '@ng-icons/lucide';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';
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
  selector: 'app-start-session-card',
  templateUrl: './start-session-card.component.html',
  imports: [
    NgIconComponent,
    HlmAlertDialogDescriptionDirective,
    HlmAlertDialogTitleDirective,
    HlmButtonModule,
    HlmSkeletonComponent,
    HlmAlertDialogActionButtonDirective,
    HlmAlertDialogCancelButtonDirective,
    HlmAlertDialogComponent,
    HlmAlertDialogContentComponent,
    HlmAlertDialogFooterComponent,
    HlmAlertDialogHeaderComponent,
    BrnAlertDialogTriggerDirective,
    BrnAlertDialogContentDirective
  ],
  providers: [provideIcons({ lucidePlus, lucideBotMessageSquare })]
})
export class StartSessionCardComponent {
  createNewSession = output<void>();
  isLoading = input<boolean>();
  hasSessions = input<boolean>(false);
  isLastSessionClosed = input<boolean>(true);
}
