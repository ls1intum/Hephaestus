import { Component, computed, input } from '@angular/core';
import { PullRequest, PullRequestLabel, PullRequestReviewDTO } from '@app/core/modules/openapi';
import { NgIcon } from '@ng-icons/core';
import { octCheck, octComment, octFileDiff, octGitPullRequest, octGitPullRequestClosed, octX } from '@ng-icons/octicons';
import dayjs from 'dayjs';
import { NgStyle } from '@angular/common';
import { cn } from '@app/utils';

@Component({
  selector: 'app-issue-card',
  templateUrl: './issue-card.component.html',
  imports: [NgIcon, NgStyle],
  standalone: true
})
export class IssueCardComponent {
  class = input('');
  title = input.required<string>();
  number = input.required<number>();
  additions = input.required<number>();
  deletions = input.required<number>();
  url = input.required<string>();
  repositoryName = input.required<string>();
  reviews = input<Set<PullRequestReviewDTO>>();
  createdAt = input.required<string>();
  state = input.required<PullRequest.StateEnum>();
  pullRequestLabels = input.required<Set<PullRequestLabel> | undefined>();
  protected readonly octCheck = octCheck;
  protected readonly octX = octX;
  protected readonly octComment = octComment;
  protected readonly octGitPullRequest = octGitPullRequest;
  protected readonly octFileDiff = octFileDiff;
  protected readonly octGitPullRequestClosed = octGitPullRequestClosed;

  displayCreated = computed(() => dayjs(this.createdAt()));

  getMostRecentReview() {
    if (!this.reviews()) {
      return null;
    }
    return Array.from(this.reviews()!).reduce((latest, review) => {
      return new Date(review.updatedAt || 0) > new Date(latest.updatedAt || 0) ? review : latest;
    });
  }

  calculateLabelColors(githubColor: string | undefined) {
    if (!githubColor) {
      return {
        borderColor: '#27272a',
        color: '#000000',
        backgroundColor: '#09090b'
      };
    }
    const borderColor = this.shadeHexColor(githubColor, -0.5);
    const backgroundColor = this.shadeHexColor(githubColor, -0.75);
    console.log('borderColor', {
      borderColor: borderColor,
      color: githubColor,
      backgroundColor: backgroundColor
    });
    return {
      borderColor: borderColor,
      color: `#${githubColor}`,
      backgroundColor: backgroundColor
    };
  }

  shadeHexColor(color: string, percent: number) {
    let f = parseInt(color, 16),
      t = percent < 0 ? 0 : 255,
      p = percent < 0 ? percent * -1 : percent,
      R = f >> 16,
      G = (f >> 8) & 0x00ff,
      B = f & 0x0000ff;
    return '#' + (0x1000000 + (Math.round((t - R) * p) + R) * 0x10000 + (Math.round((t - G) * p) + G) * 0x100 + (Math.round((t - B) * p) + B)).toString(16).slice(1);
  }

  computedClass = computed(() => cn('border border-border bg-card rounded-lg p-4 w-72', this.class()));
}
