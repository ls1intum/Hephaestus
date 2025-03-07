import { Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { octGitPullRequest, octGitPullRequestDraft, octGitMerge, octIssueOpened } from '@ng-icons/octicons';
import { HlmCardDirective } from '@spartan-ng/ui-card-helm';
import { PullRequest } from '../messages/message-parser';
import { lucideExternalLink } from '@ng-icons/lucide';

@Component({
  selector: 'app-prs-overview',
  templateUrl: './prs-overview.component.html',
  providers: [provideIcons({ octGitPullRequest, octGitPullRequestDraft, octGitMerge, octIssueOpened, lucideExternalLink })],
  imports: [CommonModule, NgIconComponent, HlmCardDirective]
})
export class PrsOverviewComponent {
  pullRequests = input<PullRequest[]>([]);

  getPrIcon(pr: { isDraft: boolean; isMerged: boolean; state: string }): string {
    if (pr.isDraft) return 'octGitPullRequestDraft';
    if (pr.isMerged) return 'octGitMerge';
    if (pr.state === 'OPEN') return 'octGitPullRequest';
    return 'octIssueOpened'; // fallback
  }
}
