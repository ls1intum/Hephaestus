import { Component, computed, input } from '@angular/core';
import { PullRequestReview, PullRequestReviewDTO } from '@app/core/modules/openapi';
import { NgIcon } from '@ng-icons/core';
import { octCheck, octComment, octFileDiff, octGitPullRequest, octGitPullRequestClosed } from '@ng-icons/octicons';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';

dayjs.extend(relativeTime);

@Component({
  selector: 'app-profile-activity-card',
  templateUrl: './profile-activity-card.component.html',
  imports: [NgIcon],
  standalone: true
})
export class ProfileActivityCardComponent {
  review = input.required<PullRequestReviewDTO>();
  protected readonly octCheck = octCheck;
  protected readonly octFileDiff = octFileDiff;
  protected readonly octComment = octComment;
  protected readonly octGitPullRequest = octGitPullRequest;
  protected readonly octGitPullRequestClosed = octGitPullRequestClosed;

  displayAge = computed(() => dayjs(this.review().createdAt).fromNow());

  reviewStateProps = computed(() => {
    if (this.review().state === 'APPROVED') {
      return {
        icon: this.octCheck,
        color: 'text-github-success-foreground',
        text: 'Approved'
      };
    } else if (this.review().state === 'CHANGES_REQUESTED') {
      return {
        icon: this.octFileDiff,
        color: 'text-github-danger-foreground',
        text: 'Changes requested'
      };
    } else {
      return {
        icon: this.octComment,
        color: 'text-github-neutral-foreground',
        text: 'Commented'
      };
    }
  });

  toTitleCase(str: string) {
    return str
      .replaceAll('_', ' ')
      .split(' ')
      .map((s) => s.replace(/\w\S*/g, (txt) => txt.charAt(0).toUpperCase() + txt.slice(1).toLowerCase()))
      .join(' ');
  }

  openIssue() {
    if (this.review().pullRequest?.url) {
      window.open(this.review().pullRequest?.url, '_blank');
    }
  }
}
