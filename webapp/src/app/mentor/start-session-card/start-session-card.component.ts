import { Component, input, output } from '@angular/core';
import { LucideAngularModule, Plus, BotMessageSquare } from 'lucide-angular';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';
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
import { BrnAlertDialogTriggerDirective, BrnAlertDialogContentDirective } from '@spartan-ng/ui-alertdialog-brain';

@Component({
  selector: 'app-start-session-card',
  templateUrl: './start-session-card.component.html',
  imports: [
    LucideAngularModule,
    HlmButtonModule,
    HlmSkeletonComponent,
    HlmAlertDialogActionButtonDirective,
    HlmAlertDialogCancelButtonDirective,
    HlmAlertDialogComponent,
    HlmAlertDialogContentComponent,
    HlmAlertDialogDescriptionDirective,
    HlmAlertDialogFooterComponent,
    HlmAlertDialogHeaderComponent,
    HlmAlertDialogTitleDirective,
    BrnAlertDialogTriggerDirective,
    BrnAlertDialogContentDirective
  ]
})
export class StartSessionCardComponent {
  protected Plus = Plus;
  protected BotMessageSquare = BotMessageSquare;

  createNewSession = output<void>();
  isLoading = input<boolean>();
  hasSessions = input<boolean>(false);
  isLastSessionClosed = input<boolean>(true);
}
