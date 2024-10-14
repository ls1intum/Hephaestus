import { Component, computed, input } from '@angular/core';
import { PullRequestDTO, PullRequestReviewDTO } from '@app/core/modules/openapi';
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
  url = input<string | undefined>(undefined);
  createdAt = input.required<string>();
  state = input.required<PullRequestReviewDTO.StateEnum>();
  repositoryName = input.required<string>();
  pullRequestNumber = input.required<number>();
  pullRequestState = input.required<PullRequestDTO.StateEnum>();
  pullRequestUrl = input.required<string>();

  protected readonly octCheck = octCheck;
  protected readonly octFileDiff = octFileDiff;
  protected readonly octComment = octComment;
  protected readonly octGitPullRequest = octGitPullRequest;
  protected readonly octGitPullRequestClosed = octGitPullRequestClosed;

  displayAge = computed(() => dayjs(this.createdAt()).fromNow());

  reviewStateProps = computed(() => {
    switch (this.state()) {
      case 'APPROVED':
        return {
          icon: this.octCheck,
          color: 'text-github-success-foreground',
          text: 'Approved'
        };
      case 'CHANGES_REQUESTED':
        return {
          icon: this.octFileDiff,
          color: 'text-github-danger-foreground',
          text: 'Changes requested'
        };
      default:
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
}
