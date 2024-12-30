import { Component, computed, input } from '@angular/core';
import { PullRequestInfo, LabelInfo, PullRequestBadPractice } from '@app/core/modules/openapi';
import { NgIcon } from '@ng-icons/core';
import { octCheck, octComment, octFileDiff, octGitPullRequest, octGitPullRequestClosed, octGitPullRequestDraft, octGitMerge, octX } from '@ng-icons/octicons';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';

import dayjs from 'dayjs';
import { BadPracticeCardComponent } from '@app/user/bad-practice-card/bad-practice-card.component';
import { BrnSeparatorComponent } from '@spartan-ng/ui-separator-brain';
import { HlmSeparatorDirective } from '@spartan-ng/ui-separator-helm';
import { BrnCollapsibleComponent, BrnCollapsibleContentComponent, BrnCollapsibleTriggerDirective } from '@spartan-ng/ui-collapsible-brain';

@Component({
  selector: 'app-pull-request-bad-practice-card',
  templateUrl: './pull-request-bad-practice-card.component.html',
  imports: [
    NgIcon,
    HlmCardModule,
    HlmSkeletonComponent,
    BadPracticeCardComponent,
    BrnSeparatorComponent,
    HlmSeparatorDirective,
    BrnCollapsibleComponent,
    BrnCollapsibleContentComponent,
    BrnCollapsibleTriggerDirective
  ],
  standalone: true
})
export class PullRequestBadPracticeCardComponent {
  protected readonly octCheck = octCheck;
  protected readonly octX = octX;
  protected readonly octComment = octComment;
  protected readonly octFileDiff = octFileDiff;

  isLoading = input(false);
  class = input('');
  title = input<string>();
  number = input<number>();
  additions = input<number>();
  deletions = input<number>();
  htmlUrl = input<string>();
  repositoryName = input<string>();
  createdAt = input<string>();
  state = input<PullRequestInfo.StateEnum>();
  isDraft = input<boolean>();
  isMerged = input<boolean>();
  pullRequestLabels = input<Array<LabelInfo>>();
  badPractices = input<Array<PullRequestBadPractice>>();

  displayCreated = computed(() => dayjs(this.createdAt()));
  displayTitle = computed(() => (this.title() ?? '').replace(/`([^`]+)`/g, '<code class="textCode">$1</code>'));

  issueIconAndColor = computed(() => {
    var icon: string;
    var color: string;

    if (this.state() === PullRequestInfo.StateEnum.Open) {
      if (this.isDraft()) {
        icon = octGitPullRequestDraft;
        color = 'text-github-muted-foreground';
      } else {
        icon = octGitPullRequest;
        color = 'text-github-open-foreground';
      }
    } else {
      if (this.isMerged()) {
        icon = octGitMerge;
        color = 'text-github-done-foreground';
      } else {
        icon = octGitPullRequestClosed;
        color = 'text-github-closed-foreground';
      }
    }

    return { icon, color };
  });
}
