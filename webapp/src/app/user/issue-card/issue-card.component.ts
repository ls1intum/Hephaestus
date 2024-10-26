import { Component, computed, input } from '@angular/core';
import { PullRequestInfo, LabelInfo } from '@app/core/modules/openapi';
import { NgIcon } from '@ng-icons/core';
import { octCheck, octComment, octFileDiff, octGitPullRequest, octGitPullRequestClosed, octGitPullRequestDraft, octGitMerge, octX } from '@ng-icons/octicons';
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
  protected readonly octCheck = octCheck;
  protected readonly octX = octX;
  protected readonly octComment = octComment;
  protected readonly octFileDiff = octFileDiff;

  isLoading = input(false);
  class = input('');
  title = input<string>();
  number = input<number>();
  additions = input<number>();
  deletions = input<number>();
  htmlUrl = input<string>();
  repositoryName = input<string>();
  createdAt = input<string>();
  state = input<PullRequestInfo.StateEnum>();
  isDraft = input<boolean>();
  isMerged = input<boolean>();
  pullRequestLabels = input<Array<LabelInfo>>();

  displayCreated = computed(() => dayjs(this.createdAt()));
  displayTitle = computed(() => (this.title() ?? '').replace(/`([^`]+)`/g, '<code class="textCode">$1</code>'));
  computedClass = computed(() => cn('w-72', !this.isLoading() ? 'hover:bg-accent/50 cursor-pointer' : '', this.class()));

  issueIconAndColor = computed(() => {
    var icon: string;
    var color: string;

    if (this.state() === PullRequestInfo.StateEnum.Open) {
      if (this.isDraft()) {
        icon = octGitPullRequestDraft;
        color = 'text-github-muted-foreground';
      } else {
        icon = octGitPullRequest;
        color = 'text-github-open-foreground';
      }
    } else {
      if (this.isMerged()) {
        icon = octGitMerge;
        color = 'text-github-done-foreground';
      } else {
        icon = octGitPullRequestClosed;
        color = 'text-github-closed-foreground';
      }
    }

    return { icon, color };
  });

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
