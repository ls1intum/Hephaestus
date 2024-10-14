import { Component, computed, input } from '@angular/core';
import { PullRequestReviewDTO } from '@app/core/modules/openapi';
import { NgIcon } from '@ng-icons/core';
import { octCheck, octComment, octFileDiff, octGitPullRequest, octGitPullRequestClosed } from '@ng-icons/octicons';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';

dayjs.extend(relativeTime);

type ReviewActivityProps = {
  state?: PullRequestReviewDTO.StateEnum;
  createdAt?: string;
};

type PullRequestProps = {
  number?: number;
  title?: string;
  url?: string;
};

@Component({
  selector: 'app-review-activity-card',
  templateUrl: './review-activity-card.component.html',
  imports: [NgIcon, HlmCardModule, HlmSkeletonComponent],
  standalone: true,
  styles: `
    :host {
      code {
        @apply bg-github-muted rounded px-1 py-0.5;
      }
    }
  `
})
export class ReviewActivityCardComponent {
  protected readonly octCheck = octCheck;
  protected readonly octFileDiff = octFileDiff;
  protected readonly octComment = octComment;
  protected readonly octGitPullRequest = octGitPullRequest;
  protected readonly octGitPullRequestClosed = octGitPullRequestClosed;

  isLoading = input<boolean>(false);
  reviewActivity = input<ReviewActivityProps>();
  pullRequest = input<PullRequestProps>();
  repositoryNameWithOwner = input<string>();

  relativeActivityTime = computed(() => dayjs(this.reviewActivity()?.createdAt).fromNow());
  displayPullRequestTitle = computed(() => (this.pullRequest()?.title ?? '').replace(/`([^`]+)`/g, '<code>$1</code>'));

  reviewStateProps = computed(() => {
    switch (this.reviewActivity()?.state) {
      case PullRequestReviewDTO.StateEnum.Approved:
        return {
          icon: this.octCheck,
          color: 'text-github-success-foreground',
          skeletonColor: 'bg-green-500/30'
        };
      case PullRequestReviewDTO.StateEnum.ChangesRequested:
        return {
          icon: this.octFileDiff,
          color: 'text-github-danger-foreground',
          skeletonColor: 'bg-destructive/20'
        };
      default:
        return {
          icon: this.octComment,
          color: 'text-github-neutral-foreground',
          skeletonColor: 'bg-neutral-500/20'
        };
    }
  });
}
