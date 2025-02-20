import { Component, computed, input } from '@angular/core';
import { PullRequestBaseInfo, PullRequestReviewInfo } from '@app/core/modules/openapi';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { octCheck, octComment, octFileDiff, octGitPullRequest, octGitPullRequestClosed } from '@ng-icons/octicons';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';
import { HlmIconDirective } from '@spartan-ng/ui-icon-helm';
import { HlmTooltipTriggerDirective } from '@spartan-ng/ui-tooltip-helm';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import dayjs from 'dayjs/esm';
import relativeTime from 'dayjs/esm/plugin/relativeTime';
import { lucideAward } from '@ng-icons/lucide';
import { cn } from '@app/utils';

dayjs.extend(relativeTime);

type ReviewStateCases = {
  [key: string]: {
    icon: string;
    color: string;
    skeletonColor: string;
  };
};

@Component({
  selector: 'app-review-activity-card',
  templateUrl: './review-activity-card.component.html',
  imports: [NgIcon, HlmCardModule, HlmSkeletonComponent, HlmIconDirective, HlmTooltipTriggerDirective, HlmButtonModule],
  providers: [provideIcons({ lucideAward })]
})
export class ReviewActivityCardComponent {
  protected readonly octCheck = octCheck;
  protected readonly octFileDiff = octFileDiff;
  protected readonly octComment = octComment;
  protected readonly octGitPullRequest = octGitPullRequest;
  protected readonly octGitPullRequestClosed = octGitPullRequestClosed;

  isLoading = input(false);
  class = input('');
  state = input<PullRequestReviewInfo.StateEnum>();
  submittedAt = input<string>();
  htmlUrl = input<string>();
  pullRequest = input<PullRequestBaseInfo>();
  repositoryName = input<string>();
  score = input<number>();

  computedClass = computed(() => cn('flex flex-col gap-1 p-6 hover:bg-accent/50 container-inline-size', this.class()));
  relativeActivityTime = computed(() => dayjs(this.submittedAt()).fromNow());
  displayPullRequestTitle = computed(() => (this.pullRequest()?.title ?? '').replace(/`([^`]+)`/g, '<code class="textCode">$1</code>'));

  reviewStateCases: ReviewStateCases = {
    [PullRequestReviewInfo.StateEnum.Approved]: {
      icon: this.octCheck,
      color: 'text-github-success-foreground',
      skeletonColor: 'bg-green-500/30'
    },
    [PullRequestReviewInfo.StateEnum.ChangesRequested]: {
      icon: this.octFileDiff,
      color: 'text-github-danger-foreground',
      skeletonColor: 'bg-destructive/20'
    },
    [PullRequestReviewInfo.StateEnum.Commented]: {
      icon: this.octComment,
      color: 'text-github-neutral-foreground',
      skeletonColor: 'bg-neutral-500/20'
    }
  };

  skeletonColorForReviewState = computed(() => {
    if (this.isLoading()) {
      const colors = Object.values(this.reviewStateCases).map((value) => value.skeletonColor);
      return colors[Math.floor(Math.random() * colors.length)];
    }
    return '';
  });

  reviewStateProps = computed(() => {
    const props = this.state() ? this.reviewStateCases[this.state()!] : undefined;
    return props ?? this.reviewStateCases[PullRequestReviewInfo.StateEnum.Commented];
  });
}
