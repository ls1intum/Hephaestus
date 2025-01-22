import { Component, inject, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgIconComponent } from '@ng-icons/core';
import { octGitPullRequest, octGitPullRequestDraft, octGitMerge } from '@ng-icons/octicons';
import { HlmCardDirective } from '@spartan-ng/ui-card-helm';
import { PullRequest } from '../messages/message-parser';


@Component({
  selector: 'app-prs-overview',
  templateUrl: './prs-overview.component.html',
  imports: [CommonModule, NgIconComponent, HlmCardDirective]
})
export class PrsOverviewComponent {
  protected OctGitPullRequest = octGitPullRequest;
  protected OctGitPullRequestDraft = octGitPullRequestDraft;
  protected OctGitMerge = octGitMerge;

  pullRequests = input<PullRequest[]>([]);
  
}
