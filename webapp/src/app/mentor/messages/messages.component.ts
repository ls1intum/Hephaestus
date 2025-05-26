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
import { ChatInputComponent } from '@app/mentor/chat-input/chat-input.component';
import {
  HlmCardContentDirective, HlmCardDescriptionDirective,
  HlmCardDirective,
  HlmCardHeaderDirective, HlmCardImports,
  HlmCardTitleDirective
} from '@spartan-ng/ui-card-helm';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { HlmInputDirective } from '@spartan-ng/ui-input-helm';

@Component({
  selector: 'app-messages',
  templateUrl: './messages.component.html',
  imports: [
    CommonModule,
    NgIconComponent,
    HlmAvatarModule,
    HlmSkeletonComponent,
    ChatSummaryComponent,
    PrsOverviewComponent,
    ChatInputComponent,
    HlmCardDirective,
    HlmCardContentDirective,
    HlmCardTitleDirective,
    HlmCardHeaderDirective,
    HlmCardDescriptionDirective,
    HlmCardImports,
    HlmButtonDirective,
    HlmInputDirective
  ],
  providers: [provideIcons({ lucideBotMessageSquare })]
})
export class MessagesComponent {
  protected Message = Message;

  securityStore = inject(SecurityStore);
  messages = input<Message[]>([]);
  isLoading = input<boolean>(false);

  getSummary = getSummary;
  getPullRequests = getPullRequests;
}
