import { Component, inject } from '@angular/core';
import { lastValueFrom } from 'rxjs';
import { WorkspaceService, TeamService } from '@app/core/modules/openapi';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { WorkspaceTeamsTableComponent } from './table/teams-table.component';

@Component({
    selector: 'app-workspace-teams',
    imports: [WorkspaceTeamsTableComponent],
    template: `
    <h1 class="text-3xl font-bold mb-4">Teams</h1>
    <app-workspace-teams-table [teamData]="teamsQuery.data()" [isLoading]="teamsQuery.isPending() || teamsQuery.isRefetching()" [allRepositories]="allReposQuery.data()" />
  `
})
export class WorkspaceTeamsComponent {
  protected teamService = inject(TeamService);
  protected workspaceService = inject(WorkspaceService);

  teamsQuery = injectQuery(() => ({
    queryKey: ['workspace', 'teams'],
    queryFn: async () => lastValueFrom(this.teamService.getTeams())
  }));

  allReposQuery = injectQuery(() => ({
    queryKey: ['workspace', 'repositoriesToMonitor'],
    queryFn: async () => lastValueFrom(this.workspaceService.getRepositoriesToMonitor())
  }));
}
