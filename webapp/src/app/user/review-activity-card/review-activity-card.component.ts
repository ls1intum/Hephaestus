import { Component, computed, input } from '@angular/core';
import { PullRequestReviewDTO } from '@app/core/modules/openapi';
import { NgIcon } from '@ng-icons/core';
import { octCheck, octComment, octFileDiff, octGitPullRequest, octGitPullRequestClosed } from '@ng-icons/octicons';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';

dayjs.extend(relativeTime);

type PullRequestProps = {
  number: number;
  title: string;
  url: string;
};

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
  imports: [NgIcon, HlmCardModule, HlmSkeletonComponent],
  standalone: true,
  styles: `
    :host {
      .containerSize {
        container-type: inline-size;
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
  state = input<PullRequestReviewDTO.StateEnum>();
  createdAt = input<string>();
  pullRequest = input<PullRequestProps>();
  repositoryName = input<string>();

  relativeActivityTime = computed(() => dayjs(this.createdAt()).fromNow());
  displayPullRequestTitle = computed(() => (this.pullRequest()?.title ?? '').replace(/`([^`]+)`/g, '<code class="textCode">$1</code>'));

  reviewStateCases: ReviewStateCases = {
    [PullRequestReviewDTO.StateEnum.Approved]: {
      icon: this.octCheck,
      color: 'text-github-success-foreground',
      skeletonColor: 'bg-green-500/30'
    },
    [PullRequestReviewDTO.StateEnum.ChangesRequested]: {
      icon: this.octFileDiff,
      color: 'text-github-danger-foreground',
      skeletonColor: 'bg-destructive/20'
    },
    [PullRequestReviewDTO.StateEnum.Commented]: {
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
    return props ?? this.reviewStateCases[PullRequestReviewDTO.StateEnum.Commented];
  });
}
