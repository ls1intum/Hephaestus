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
import { MetaService, TeamService } from '@app/core/modules/openapi';

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
  protected after = computed(() => this.queryParams().get('after') ?? dayjs().isoWeekday(1).format('YYYY-MM-DD'));
  protected before = computed(() => this.queryParams().get('before') ?? dayjs().format('YYYY-MM-DD'));
  protected teams = computed(() => this.queryParams().get('team') ?? 'all');

  query = injectQuery(() => ({
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
}
