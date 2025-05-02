import { AfterViewInit, Component, computed, inject, input, ViewChild } from '@angular/core';
import { PullRequestInfo, LabelInfo, PullRequestBadPractice, ActivityService } from '@app/core/modules/openapi';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { octCheck, octComment, octFileDiff, octGitPullRequest, octGitPullRequestClosed, octGitPullRequestDraft, octGitMerge, octX, octFold, octSync } from '@ng-icons/octicons';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';

import dayjs from 'dayjs/esm';
import { BadPracticeCardComponent } from '@app/user/bad-practice-card/bad-practice-card.component';
import { BrnSeparatorComponent } from '@spartan-ng/brain/separator';
import { HlmSeparatorDirective } from '@spartan-ng/ui-separator-helm';
import { BrnCollapsibleComponent, BrnCollapsibleContentComponent, BrnCollapsibleTriggerDirective } from '@spartan-ng/brain/collapsible';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { GithubLabelComponent } from '@app/ui/github-label/github-label.component';
import { formatTitle } from '@app/utils';
import { HlmSpinnerComponent } from '@spartan-ng/ui-spinner-helm';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { toast } from 'ngx-sonner';

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
  providers: [provideIcons({ octCheck, octX, octComment, octFileDiff, octGitPullRequest, octGitPullRequestClosed, octGitPullRequestDraft, octGitMerge, octFold, octSync })]
})
export class PullRequestBadPracticeCardComponent implements AfterViewInit {
  activityService = inject(ActivityService);
  queryClient = inject(QueryClient);

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
  badPracticeSummary = input<string>('');
  openCard = input<boolean>(false);
  currUserIsDashboardUser = input<boolean>(false);

  displayCreated = computed(() => dayjs(this.createdAt()));
  displayTitle = computed(() => formatTitle(this.title() ?? ''));
  expandEnabled = computed(() => this.badPractices()?.length !== 0);

  @ViewChild(BrnCollapsibleTriggerDirective) collapsibleTrigger!: BrnCollapsibleTriggerDirective;

  ngAfterViewInit() {
    if (this.openCard()) {
      this.collapsibleTrigger.toggleCollapsible();
    }
  }

  issueIconAndColor = computed(() => {
    let icon: string;
    let color: string;

    if (this.state() === PullRequestInfo.StateEnum.Open) {
      if (this.isDraft()) {
        icon = 'octGitPullRequestDraft';
        color = 'text-github-muted-foreground';
      } else {
        icon = 'octGitPullRequest';
        color = 'text-github-open-foreground';
      }
    } else {
      if (this.isMerged()) {
        icon = 'octGitMerge';
        color = 'text-github-done-foreground';
      } else {
        icon = 'octGitPullRequestClosed';
        color = 'text-github-closed-foreground';
      }
    }

    return { icon, color };
  });

  detectBadPracticesForPr = (prId: number) => {
    this.detectBadPracticesForPrMutation.mutate(prId);
  };

  detectBadPracticesForPrMutation = injectMutation(() => ({
    mutationFn: (prId: number) => lastValueFrom(this.activityService.detectBadPracticesForPullRequest(prId)),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: ['activity'] });
      if (this.collapsibleTrigger.state() === 'closed') {
        this.collapsibleTrigger.toggleCollapsible();
      }
    },
    onError: () => {
      this.showToast();
    }
  }));

  showToast() {
    toast('Something went wrong...', {
      description: 'This pull request has not changed since the last detection. Try changing status or description, then run the detection again.'
    });
  }
}
