import { Component, computed, input } from '@angular/core';
import { PullRequest, PullRequestLabel, PullRequestReview } from '@app/core/modules/openapi';
import { NgIcon } from '@ng-icons/core';
import { octCheck, octComment, octFileDiff, octGitPullRequest, octGitPullRequestClosed, octX } from '@ng-icons/octicons';
import dayjs, { Dayjs } from 'dayjs';
import { NgStyle } from '@angular/common';

@Component({
  selector: 'app-issue-card',
  templateUrl: './issue-card.component.html',
  imports: [NgIcon, NgStyle],
  standalone: true
})
export class IssueCardComponent {
  title = input.required<string>();
  number = input.required<number>();
  additions = input.required<number>();
  deletions = input.required<number>();
  url = input.required<string>();
  repositoryName = input.required<string>();
  reviews = input<Array<PullRequestReview>>([]);
  createdAt = input.required<string>();
  state = input.required<PullRequest.StateEnum>();
  pullRequestLabels = input.required<Array<PullRequestLabel>>();
  protected readonly octCheck = octCheck;
  protected readonly octX = octX;
  protected readonly octComment = octComment;
  protected readonly octGitPullRequest = octGitPullRequest;
  protected readonly octFileDiff = octFileDiff;
  protected readonly octGitPullRequestClosed = octGitPullRequestClosed;

  displayCreated = computed(() => dayjs(this.createdAt()));

  getMostRecentReview() {
    return this.reviews().reduce((latest, review) => {
      return new Date(review.updatedAt || 0) > new Date(latest.updatedAt || 0) ? review : latest;
    });
  }

  openIssue() {
    window.open(this.url());
  }
}
