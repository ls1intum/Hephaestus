import { Component, computed, inject } from '@angular/core';
import { ActivityService } from '@app/core/modules/openapi';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { combineLatest, lastValueFrom, map, timer } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { IssueCardComponent } from '@app/user/issue-card/issue-card.component';
import { BadPracticeCardComponent } from '@app/user/bad-practice-card/bad-practice-card.component';

@Component({
  selector: 'app-activity-dashboard',
  standalone: true,
  imports: [IssueCardComponent, BadPracticeCardComponent],
  templateUrl: './activity-dashboard.component.html',
  styles: ``
})
export class ActivityDashboardComponent {
  activityService = inject(ActivityService);

  protected userLogin: string | null = null;
  protected numberOfPullRequests = computed(() => this.query.data()?.pullRequests?.length ?? 0);
  protected numberOfBadPractices = computed(() => this.query.data()?.pullRequests?.reduce((acc, pr) => acc + (pr.badPractices?.length ?? 0), 0) ?? 0);

  constructor(private route: ActivatedRoute) {
    this.userLogin = this.route.snapshot.paramMap.get('id');
  }

  query = injectQuery(() => ({
    queryKey: ['user', { id: this.userLogin }],
    enabled: !!this.userLogin,
    queryFn: async () => lastValueFrom(combineLatest([this.activityService.getActivityByUser(this.userLogin!), timer(400)]).pipe(map(([activity]) => activity)))
  }));
}
