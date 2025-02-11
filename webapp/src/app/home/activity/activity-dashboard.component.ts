import { Component, computed, inject } from '@angular/core';
import { ActivityService } from '@app/core/modules/openapi';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { combineLatest, lastValueFrom, map, timer } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { PullRequestBadPracticeCardComponent } from '@app/user/pull-request-bad-practice-card/pull-request-bad-practice-card.component';
import { LucideAngularModule, RefreshCcw } from 'lucide-angular';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { HlmSpinnerComponent } from '@spartan-ng/ui-spinner-helm';

@Component({
  selector: 'app-activity-dashboard',
  standalone: true,
  imports: [PullRequestBadPracticeCardComponent, LucideAngularModule, HlmButtonDirective, HlmSpinnerComponent],
  templateUrl: './activity-dashboard.component.html',
  styles: ``
})
export class ActivityDashboardComponent {
  activityService = inject(ActivityService);
  queryClient = inject(QueryClient);

  protected userLogin: string | null = null;
  protected numberOfPullRequests = computed(() => this.query.data()?.pullRequests?.length ?? 0);
  protected numberOfBadPractices = computed(() => this.query.data()?.pullRequests?.reduce((acc, pr) => acc + (pr.badPractices?.length ?? 0), 0) ?? 0);
  protected readonly RefreshCcw = RefreshCcw;

  constructor(private route: ActivatedRoute) {
    this.userLogin = this.route.snapshot.paramMap.get('id');
  }

  query = injectQuery(() => ({
    queryKey: ['activity', { id: this.userLogin }],
    enabled: !!this.userLogin,
    queryFn: async () => lastValueFrom(combineLatest([this.activityService.getActivityByUser(this.userLogin!), timer(400)]).pipe(map(([activity]) => activity)))
  }));

  detectBadPracticesMutation = injectMutation(() => ({
    mutationFn: () => lastValueFrom(this.activityService.detectBadPracticesByUser(this.userLogin!)),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: ['activity', { id: this.userLogin }] });
    }
  }));
}
