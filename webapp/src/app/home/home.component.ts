import dayjs from 'dayjs';
import isoWeek from 'dayjs/plugin/isoWeek';
import { combineLatest, timer, lastValueFrom, map } from 'rxjs';
import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { LucideAngularModule, CircleX } from 'lucide-angular';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { LeaderboardService } from '@app/core/modules/openapi/api/leaderboard.service';
import { LeaderboardComponent } from '@app/home/leaderboard/leaderboard.component';
import { LeaderboardFilterComponent } from './leaderboard/filter/filter.component';
import { SecurityStore } from '@app/core/security/security-store.service';
import { HlmAlertModule } from '@spartan-ng/ui-alert-helm';
import { TeamService } from '@app/core/modules/openapi';
import { MetaService } from '@app/core/modules/openapi';

dayjs.extend(isoWeek);

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [LeaderboardComponent, LeaderboardFilterComponent, HlmAlertModule, LucideAngularModule],
  templateUrl: './home.component.html'
})
export class HomeComponent {
  protected CircleX = CircleX;

  securityStore = inject(SecurityStore);
  metaService = inject(MetaService);
  leaderboardService = inject(LeaderboardService);
  teamService = inject(TeamService);

  signedIn = this.securityStore.signedIn;
  user = this.securityStore.loadedUser;

  private readonly route = inject(ActivatedRoute);
  private queryParams = toSignal(this.route.queryParamMap, { requireSync: true });

  protected leaderboardSchedule = computed(() => {
    const timeParts = this.metaQuery.data()?.scheduledTime?.split(':') ?? ['09', '00'];
    return {
      day: Number.parseInt(this.metaQuery.data()?.scheduledDay ?? '2'),
      hour: Number.parseInt(timeParts[0]),
      minute: Number.parseInt(timeParts[1] ?? '0')
    };
  });

  protected after = computed(() => {
    const afterParam = this.queryParams().get('after');
    if (afterParam) return afterParam;

    let defaultDate = dayjs().isoWeekday(this.leaderboardSchedule().day).startOf('hour').hour(this.leaderboardSchedule().hour).minute(this.leaderboardSchedule().minute);
    if (defaultDate.isAfter(dayjs()) || defaultDate.isSame(dayjs(), 'day')) {
      defaultDate = defaultDate.subtract(1, 'week');
    }
    return defaultDate.format();
  });
  protected before = computed(() => this.queryParams().get('before') ?? dayjs().format());
  protected teams = computed(() => this.queryParams().get('team') ?? 'all');

  query = injectQuery(() => ({
    enabled: this.metaQuery.data !== undefined,
    queryKey: ['leaderboard', { after: this.after(), before: this.before(), repository: this.teams() }],
    queryFn: async () =>
      lastValueFrom(
        combineLatest([this.leaderboardService.getLeaderboard(this.after(), this.before(), this.teams() !== 'all' ? this.teams() : undefined), timer(500)]).pipe(
          map(([leaderboard]) => leaderboard)
        )
      )
  }));

  protected _teams = computed(() => this.teamsQuery.data()?.map((team) => team.name) ?? []);
  teamsQuery = injectQuery(() => ({
    queryKey: ['teams'],
    queryFn: async () => lastValueFrom(this.teamService.getTeams())
  }));
  metaQuery = injectQuery(() => ({
    queryKey: ['meta'],
    queryFn: async () => lastValueFrom(this.metaService.getMetaData())
  }));
}
