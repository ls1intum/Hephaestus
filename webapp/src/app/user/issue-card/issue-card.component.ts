import { Component, computed, input } from '@angular/core';
import { PullRequest, PullRequestLabel, PullRequestReviewDTO } from '@app/core/modules/openapi';
import { NgIcon } from '@ng-icons/core';
import { octCheck, octComment, octFileDiff, octGitPullRequest, octGitPullRequestClosed, octX } from '@ng-icons/octicons';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';
import dayjs from 'dayjs';
import { cn } from '@app/utils';

@Component({
  selector: 'app-issue-card',
  templateUrl: './issue-card.component.html',
  imports: [NgIcon, HlmCardModule, HlmSkeletonComponent],
  styleUrls: ['./issue-card.component.scss'],
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
  displayTitle = computed(() => (this.title() ?? '').replace(/`([^`]+)`/g, '<code class="textCode">$1</code>'));
  computedClass = computed(() => cn('w-72', this.class()));

  getMostRecentReview() {
    if (!this.reviews()) {
      return null;
    }
    return Array.from(this.reviews()!).reduce((latest, review) => {
      return new Date(review.updatedAt || 0) > new Date(latest.updatedAt || 0) ? review : latest;
    });
  }

  hexToRgb(hex: string) {
    const bigint = parseInt(hex, 16);
    const r = (bigint >> 16) & 255;
    const g = (bigint >> 8) & 255;
    const b = bigint & 255;

    const hsl = this.rgbToHsl(r, g, b);

    return {
      r: r,
      g: g,
      b: b,
      ...hsl
    };
  }

  rgbToHsl(r: number, g: number, b: number) {
    r /= 255;
    g /= 255;
    b /= 255;

    const max = Math.max(r, g, b);
    const min = Math.min(r, g, b);
    let h = 0,
      s = 0,
      l = (max + min) / 2;

    if (max !== min) {
      const d = max - min;
      s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
      switch (max) {
        case r:
          h = (g - b) / d + (g < b ? 6 : 0);
          break;
        case g:
          h = (b - r) / d + 2;
          break;
        case b:
          h = (r - g) / d + 4;
          break;
      }
      h /= 6;
    }

    h = Math.round(h * 360);
    s = Math.round(s * 100);
    l = Math.round(l * 100);

    return { h, s, l };
  }
}
