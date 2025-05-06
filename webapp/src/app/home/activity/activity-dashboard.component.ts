import { Component, computed, inject } from '@angular/core';
import { ActivityService, PullRequestBadPractice, PullRequestWithBadPractices } from '@app/core/modules/openapi';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { combineLatest, lastValueFrom, map, timer } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { PullRequestBadPracticeCardComponent } from '@app/user/pull-request-bad-practice-card/pull-request-bad-practice-card.component';
import { lucideRefreshCcw } from '@ng-icons/lucide';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { HlmSpinnerComponent } from '@spartan-ng/ui-spinner-helm';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { BadPracticeLegendCardComponent } from '@app/user/bad-practice-legend-card/bad-practice-legend-card.component';
import { SecurityStore } from '@app/core/security/security-store.service';
import { HttpResponse } from '@angular/common/http';
import { doubleDetectionString, filterGoodAndBadPractices, serverErrorString, showToast } from '@app/utils';

@Component({
  selector: 'app-activity-dashboard',
  standalone: true,
  imports: [PullRequestBadPracticeCardComponent, HlmButtonDirective, HlmSpinnerComponent, NgIcon, BadPracticeLegendCardComponent],
  providers: [provideIcons({ lucideRefreshCcw })],
  templateUrl: './activity-dashboard.component.html',
  styles: ``
})
export class ActivityDashboardComponent {
  activityService = inject(ActivityService);
  queryClient = inject(QueryClient);
  securityStore = inject(SecurityStore);

  user = this.securityStore.loadedUser;

  protected userLogin: string | undefined = undefined;
  protected openedPullRequestId: number | undefined = undefined;

  protected allBadPractices = computed(() => this.query.data()?.pullRequests?.reduce((acc, pr) => acc.concat(pr.badPractices), [] as PullRequestBadPractice[]) ?? []);
  protected numberOfPullRequests = computed(() => this.query.data()?.pullRequests?.length ?? 0);
  protected goodAndBadPractices = computed(() => filterGoodAndBadPractices(this.allBadPractices()));
  protected numberOfGoodPractices = computed(() => this.goodAndBadPractices().goodPractices.length);
  protected numberOfBadPractices = computed(() => this.goodAndBadPractices().badPractices.length);
  protected currUserIsDashboardUser = computed(() => this.user()?.username === this.userLogin);

  constructor(private route: ActivatedRoute) {
    this.userLogin = this.route.snapshot.paramMap.get('id') ?? this.user()?.username;
    this.openedPullRequestId = this.route.snapshot.queryParams['pullRequest'];
  }

  query = injectQuery(() => ({
    queryKey: ['activity', { id: this.userLogin }],
    enabled: !!this.userLogin,
    queryFn: async () => lastValueFrom(combineLatest([this.activityService.getActivityByUser(this.userLogin!), timer(400)]).pipe(map(([activity]) => activity)))
  }));

  detectBadPracticesMutation = injectMutation(() => ({
    mutationFn: () => lastValueFrom(this.activityService.detectBadPracticesByUser(this.userLogin!, "response")),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: ['activity', { id: this.userLogin }] });
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
