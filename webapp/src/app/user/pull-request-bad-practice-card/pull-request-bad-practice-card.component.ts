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
import { doubleDetectionString, filterGoodAndBadPractices, formatTitle, serverErrorString, showToast } from '@app/utils';
import { HlmSpinnerComponent } from '@spartan-ng/ui-spinner-helm';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { HlmAccordionImports } from '@spartan-ng/ui-accordion-helm';

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
    HlmSpinnerComponent,
    HlmAccordionImports
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
  updatedAt = input<string>();
  state = input<PullRequestInfo.StateEnum>();
  isDraft = input<boolean>();
  isMerged = input<boolean>();
  pullRequestLabels = input<Array<LabelInfo>>();
  badPractices = input<Array<PullRequestBadPractice>>();
  oldBadPractices = input<Array<PullRequestBadPractice>>();
  badPracticeSummary = input<string>('');
  openCard = input<boolean>(false);
  currUserIsDashboardUser = input<boolean>(false);

  displayCreated = computed(() => dayjs(this.createdAt()));
  displayUpdated = computed(() => dayjs(this.updatedAt()));
  displayTitle = computed(() => formatTitle(this.title() ?? ''));
  expandEnabled = computed(() => this.badPractices()?.length !== 0);

  protected goodAndBadPractices = computed(() => filterGoodAndBadPractices(this.badPractices() ?? []));
  protected numberOfGoodPractices = computed(() => this.goodAndBadPractices().goodPractices.length);
  protected numberOfBadPractices = computed(() => this.goodAndBadPractices().badPractices.length);
  protected numberOfResolvedPractices = computed(() => this.goodAndBadPractices().resolvedPractices.length);
  protected detectedString = computed(() => {
    if (this.numberOfBadPractices() === 0) {
      if (this.numberOfGoodPractices() === 0) {
        if (this.numberOfResolvedPractices() === 0) {
          return 'Nothing detected yet';
        }
        return 'All bad practices resolved';
      } else if (this.numberOfGoodPractices() === 1) {
        return '1 good practice detected';
      } else {
        return `${this.numberOfGoodPractices()} good practices detected`;
      }
    } else if (this.numberOfBadPractices() === 1) {
      return '1 bad practice detected';
    } else {
      return `${this.numberOfBadPractices()} bad practices detected`;
    }
  });
  protected orderedBadPractices = computed(() => this.orderBadPractices(this.badPractices() ?? []));
  protected orderedOldBadPractices = computed(() => this.orderBadPractices(this.oldBadPractices() ?? []));

  orderBadPractices(badPractices: Array<PullRequestBadPractice>): Array<PullRequestBadPractice> {
    const stateOrder = [
      PullRequestBadPractice.StateEnum.CriticalIssue,
      PullRequestBadPractice.StateEnum.NormalIssue,
      PullRequestBadPractice.StateEnum.MinorIssue,
      PullRequestBadPractice.StateEnum.GoodPractice,
      PullRequestBadPractice.StateEnum.Fixed,
      PullRequestBadPractice.StateEnum.WontFix,
      PullRequestBadPractice.StateEnum.Wrong
    ];

    return [...(badPractices ?? [])].sort((a, b) => stateOrder.indexOf(a.state) - stateOrder.indexOf(b.state));
  }

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
    mutationFn: (prId: number) => lastValueFrom(this.activityService.detectBadPracticesForPullRequest(prId, 'response')),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: ['activity'] });
      if (this.collapsibleTrigger != undefined && this.collapsibleTrigger.state() === 'closed') {
        this.collapsibleTrigger.toggleCollapsible();
      }
    },
    onError: (error: HttpResponse<never>) => {
      if (error.status === 400) {
        showToast(doubleDetectionString);
      } else {
        showToast(serverErrorString);
      }
    }
  }));
}
