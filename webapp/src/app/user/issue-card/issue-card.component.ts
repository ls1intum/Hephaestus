import { Component, computed, input } from '@angular/core';
import { PullRequestInfo, LabelInfo } from '@app/core/modules/openapi';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { octCheck, octComment, octFileDiff, octGitPullRequest, octGitPullRequestClosed, octGitPullRequestDraft, octGitMerge, octX } from '@ng-icons/octicons';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';
import { GithubLabelComponent } from '@app/ui/github-label/github-label.component';
import dayjs from 'dayjs/esm';
import { hlm } from '@spartan-ng/brain/core';

@Component({
  selector: 'app-issue-card',
  templateUrl: './issue-card.component.html',
  providers: [provideIcons({ octCheck, octX, octComment, octFileDiff, octGitPullRequest, octGitPullRequestClosed, octGitPullRequestDraft, octGitMerge })],
  imports: [NgIconComponent, HlmCardModule, HlmSkeletonComponent, GithubLabelComponent]
})
export class IssueCardComponent {
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
  computedClass = computed(() => hlm('flex flex-col gap-1 pt-6 w-72', !this.isLoading() ? 'hover:bg-accent/50 cursor-pointer' : '', this.class()));

  issueIconAndColor = computed(() => {
    var iconName: string;
    var color: string;

    if (this.state() === PullRequestInfo.StateEnum.Open) {
      if (this.isDraft()) {
        iconName = 'octGitPullRequestDraft';
        color = 'text-github-muted-foreground';
      } else {
        iconName = 'octGitPullRequest';
        color = 'text-github-open-foreground';
      }
    } else {
      if (this.isMerged()) {
        iconName = 'octGitMerge';
        color = 'text-github-done-foreground';
      } else {
        iconName = 'octGitPullRequestClosed';
        color = 'text-github-closed-foreground';
      }
    }

    return { iconName, color };
  });
}
