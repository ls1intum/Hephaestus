import { Component, input } from '@angular/core';
import { PullRequest, PullRequestReview } from '@app/core/modules/openapi';
import { NgIcon } from '@ng-icons/core';
import { octCheck, octComment, octFileDiff, octGitPullRequest, octX } from '@ng-icons/octicons';

@Component({
  selector: 'app-issue-card',
  templateUrl: './app-issue-card.component.html',
  imports: [NgIcon],
  standalone: true
})
export class AppIssueCardComponent {
  pullRequest = input.required<PullRequest>();
  protected readonly octCheck = octCheck;
  protected readonly octX = octX;
  protected readonly octComment = octComment;
  protected readonly octGitPullRequest = octGitPullRequest;

  getMostRecentReview() {
    if (!this.pullRequest() || !this.pullRequest().reviews) {
      return null;
    }

    return Array.from(this.pullRequest().reviews || []).reduce((latest: PullRequestReview, review: PullRequestReview) => {
      return new Date(review.updatedAt || 0) > new Date(latest.updatedAt || 0) ? review : latest;
    });
  }

  protected readonly octFileDiff = octFileDiff;
}
