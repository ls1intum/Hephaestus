import { Component, inject } from '@angular/core';
import { lastValueFrom } from 'rxjs';
import { AdminService, TeamService } from '@app/core/modules/openapi';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { AdminTeamsTableComponent } from './table/teams-table.component';

@Component({
  selector: 'app-admin-teams',
  standalone: true,
  imports: [AdminTeamsTableComponent],
  template: `
    <h1 class="text-3xl font-bold mb-4">Teams</h1>
    <app-admin-teams-table [teamData]="teamsQuery.data()" [isLoading]="teamsQuery.isPending() || teamsQuery.isRefetching()" [allRepositories]="allReposQuery.data()" />
  `
})
export class AdminTeamsComponent {
  protected teamService = inject(TeamService);
  protected adminService = inject(AdminService);

  teamsQuery = injectQuery(() => ({
    queryKey: ['admin', 'teams'],
    queryFn: async () => lastValueFrom(this.teamService.getTeams())
  }));

  allReposQuery = injectQuery(() => ({
    queryKey: ['admin', 'config'],
    queryFn: async () =>  lastValueFrom(this.adminService.getConfig()),
    select: data => data.repositoriesToMonitor
  }));
}
