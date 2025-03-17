import { Component, inject } from '@angular/core';
import { TeamService } from '@app/core/modules/openapi';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';
import {
  HlmCardContentDirective,
  HlmCardDirective,
  HlmCardHeaderDirective,
  HlmCardTitleDirective
} from '@spartan-ng/ui-card-helm';

@Component({
  selector: 'app-subteams',
  imports: [HlmSkeletonComponent, HlmCardDirective, HlmCardTitleDirective, HlmCardContentDirective, HlmCardHeaderDirective],
  templateUrl: './subteams.component.html',
  styles: ``
})
export class SubteamsComponent {
  protected teamService = inject(TeamService);

  teamsQuery = injectQuery(() => ({
    queryKey: ['workspace', 'teams'],
    queryFn: async () => lastValueFrom(this.teamService.getTeams())
  }));
}
