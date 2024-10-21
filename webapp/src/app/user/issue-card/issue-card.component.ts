import { Component, computed, input } from '@angular/core';
import { PullRequest, PullRequestLabel } from '@app/core/modules/openapi';
import { NgIcon } from '@ng-icons/core';
import { octCheck, octComment, octFileDiff, octGitPullRequest, octGitPullRequestClosed, octX } from '@ng-icons/octicons';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';
import { GithubLabelComponent } from '@app/ui/github-label/github-label.component';
import dayjs from 'dayjs';
import { cn } from '@app/utils';

@Component({
  selector: 'app-issue-card',
  templateUrl: './issue-card.component.html',
  imports: [NgIcon, HlmCardModule, HlmSkeletonComponent, GithubLabelComponent],
  standalone: true
})
export class IssueCardComponent {
  protected readonly octCheck = octCheck;
  protected readonly octX = octX;
  protected readonly octComment = octComment;
  protected readonly octGitPullRequest = octGitPullRequest;
  protected readonly octFileDiff = octFileDiff;
  protected readonly octGitPullRequestClosed = octGitPullRequestClosed;

  isLoading = input(false);
  class = input('');
  title = input<string>();
  number = input<number>();
  additions = input<number>();
  deletions = input<number>();
  url = input<string>();
  repositoryName = input<string>();
  createdAt = input<string>();
  state = input<PullRequest.StateEnum>();
  pullRequestLabels = input<Set<PullRequestLabel>>();

  displayCreated = computed(() => dayjs(this.createdAt()));
  displayTitle = computed(() => (this.title() ?? '').replace(/`([^`]+)`/g, '<code class="textCode">$1</code>'));
  computedClass = computed(() => cn('w-72', !this.isLoading() ? 'hover:bg-accent/50 cursor-pointer' : '', this.class()));
}
