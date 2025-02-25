import { Component, inject, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { lucideBotMessageSquare } from '@ng-icons/lucide';
import { HlmAvatarModule } from '@spartan-ng/ui-avatar-helm';
import { SecurityStore } from '@app/core/security/security-store.service';
import { Message } from '@app/core/modules/openapi';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';
import { ChatSummaryComponent } from '../chat-summary/chat-summary.component';
import { PrsOverviewComponent } from '../prs-overview/prs-overview.component';
import { getSummary, getPullRequests } from './message-parser';

@Component({
  selector: 'app-messages',
  templateUrl: './messages.component.html',
  imports: [CommonModule, NgIconComponent, HlmAvatarModule, HlmSkeletonComponent, ChatSummaryComponent, PrsOverviewComponent],
  providers: [provideIcons({ lucideBotMessageSquare })]
})
export class MessagesComponent {
  protected Message = Message;

  securityStore = inject(SecurityStore);
  messages = input<(Message)[]>([]);
  isLoading = input<boolean>(false);

  getSummary = getSummary;
  getPullRequests = getPullRequests;
}
