import dayjs from 'dayjs';
import isoWeek from 'dayjs/plugin/isoWeek';
import { lastValueFrom } from 'rxjs';
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
import { MetaService, UserService } from '@app/core/modules/openapi';
import { LeaderboardLegendComponent } from './leaderboard/legend/legends.component';
import { LeaderboardLeagueComponent } from './leaderboard/league/league.component';

dayjs.extend(isoWeek);

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [LeaderboardComponent, LeaderboardFilterComponent, HlmAlertModule, LucideAngularModule, LeaderboardLegendComponent, LeaderboardLeagueComponent],
  templateUrl: './home.component.html'
})
export class HomeComponent {
  protected CircleX = CircleX;

  securityStore = inject(SecurityStore);
  metaService = inject(MetaService);
  leaderboardService = inject(LeaderboardService);
  userService = inject(UserService);

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

  protected afterParam = computed(() => this.queryParams().get('after'));
  protected beforeParam = computed(() => this.queryParams().get('before'));
  protected teamParam = computed(() => this.queryParams().get('team') ?? 'all');

  query = injectQuery(() => ({
    enabled: !!this.metaQuery.data() && !!this.afterParam() && !!this.beforeParam() && !!this.teamParam(),
    queryKey: ['leaderboard', { after: this.afterParam(), before: this.beforeParam(), team: this.teamParam() }],
    queryFn: async () => lastValueFrom(this.leaderboardService.getLeaderboard(this.afterParam()!, this.beforeParam()!, this.teamParam() !== 'all' ? this.teamParam() : undefined))
  }));

  protected teams = computed(() => this.metaQuery.data()?.teams?.map((team) => team.name) ?? []);
  metaQuery = injectQuery(() => ({
    queryKey: ['meta'],
    queryFn: async () => lastValueFrom(this.metaService.getMetaData())
  }));

  userMeQuery = injectQuery(() => ({
    enabled: !!this.user(),
    queryKey: ['user', { id: this.user()?.username }],
    queryFn: async () => lastValueFrom(this.userService.getUserProfile(this.user()!.username))
  }));
}
