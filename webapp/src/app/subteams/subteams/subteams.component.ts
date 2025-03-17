import { Component, computed, inject } from '@angular/core';
import { TeamInfo, TeamService } from '@app/core/modules/openapi';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';
import { HlmCardContentDirective, HlmCardDirective, HlmCardHeaderDirective, HlmCardTitleDirective } from '@spartan-ng/ui-card-helm';

@Component({
  selector: 'app-subteams',
  imports: [HlmSkeletonComponent, HlmCardDirective, HlmCardTitleDirective, HlmCardContentDirective, HlmCardHeaderDirective],
  templateUrl: './subteams.component.html',
  styles: ``
})
export class SubteamsComponent {
  protected teamService = inject(TeamService);

  sortedTeams = computed(() => {
    return this.teamsQuery.data()?.sort((a, b) => a.name.localeCompare(b.name));
  });

  sortMembers = (team: TeamInfo) => {
    return Array.from(team.members).sort((a, b) => a.name.localeCompare(b.name));
  };

  teamsQuery = injectQuery(() => ({
    queryKey: ['workspace', 'teams'],
    queryFn: async () => lastValueFrom(this.teamService.getTeams())
  }));
}
