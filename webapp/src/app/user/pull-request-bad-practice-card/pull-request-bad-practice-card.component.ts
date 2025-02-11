import { Component, computed, inject, input, output } from '@angular/core';
import { PullRequestInfo, LabelInfo, PullRequestBadPractice, ActivityService } from '@app/core/modules/openapi';
import { NgIcon } from '@ng-icons/core';
import { octCheck, octComment, octFileDiff, octGitPullRequest, octGitPullRequestClosed, octGitPullRequestDraft, octGitMerge, octX, octFold, octSync } from '@ng-icons/octicons';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';

import dayjs from 'dayjs/esm';
import { BadPracticeCardComponent } from '@app/user/bad-practice-card/bad-practice-card.component';
import { BrnSeparatorComponent } from '@spartan-ng/ui-separator-brain';
import { HlmSeparatorDirective } from '@spartan-ng/ui-separator-helm';
import { BrnCollapsibleComponent, BrnCollapsibleContentComponent, BrnCollapsibleTriggerDirective } from '@spartan-ng/ui-collapsible-brain';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { GithubLabelComponent } from '@app/ui/github-label/github-label.component';
import { cn } from '@app/utils';
import { formatTitle } from '@app/utils';
import { HlmSpinnerComponent } from '@spartan-ng/ui-spinner-helm';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

@Component({
  selector: 'app-pull-request-bad-practice-card',
  templateUrl: './pull-request-bad-practice-card.component.html',
  imports: [
    NgIcon,
    HlmCardModule,
    HlmSkeletonComponent,
    BadPracticeCardComponent,
    BrnSeparatorComponent,
    HlmSeparatorDirective,
    BrnCollapsibleComponent,
    BrnCollapsibleContentComponent,
    BrnCollapsibleTriggerDirective,
    HlmButtonDirective,
    GithubLabelComponent,
    HlmSpinnerComponent
  ],
  standalone: true
})
export class PullRequestBadPracticeCardComponent {
  activityService = inject(ActivityService);
  queryClient = inject(QueryClient);

  protected readonly octCheck = octCheck;
  protected readonly octX = octX;
  protected readonly octComment = octComment;
  protected readonly octFileDiff = octFileDiff;
  protected readonly octFold = octFold;

  isLoading = input(false);
  class = input('');
  id = input.required<number>();
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
  badPractices = input<Array<PullRequestBadPractice>>();

  detectBadPracticesForPr = output<void>();

  displayCreated = computed(() => dayjs(this.createdAt()));
  displayTitle = computed(() => formatTitle(this.title() ?? ''));
  computedClass = computed(() => cn('w-full', !this.isLoading() ? 'hover:bg-accent/50 cursor-pointer' : '', this.class()));

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
  protected readonly octSync = octSync;

  detectBadPractices = () => {
    console.log('Detecting bad practices for PR ' + this.id());
    this.detectBadPracticesForPr.emit();
  };

  detectBadPracticesForPrMutation = injectMutation(() => ({
    mutationFn: (prId: number) => lastValueFrom(this.activityService.detectBadPracticesByPr(prId)),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: ['activity'] });
    }
  }));
}
