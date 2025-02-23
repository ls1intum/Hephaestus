import { Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { octGitPullRequest, octGitPullRequestDraft, octGitMerge } from '@ng-icons/octicons';
import { HlmCardDirective } from '@spartan-ng/ui-card-helm';
import { PullRequest } from '../messages/message-parser';

@Component({
  selector: 'app-prs-overview',
  templateUrl: './prs-overview.component.html',
  providers: [provideIcons({ octGitPullRequest, octGitPullRequestDraft, octGitMerge })],
  imports: [CommonModule, NgIconComponent, HlmCardDirective]
})
export class PrsOverviewComponent {
  pullRequests = input<PullRequest[]>([]);
}
