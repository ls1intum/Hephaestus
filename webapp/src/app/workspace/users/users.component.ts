import { Component, inject } from '@angular/core';
import { combineLatest, lastValueFrom, map, timer } from 'rxjs';
import { WorkspaceService, TeamService } from '@app/core/modules/openapi';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { WorkspaceUsersTableComponent } from './table/users-table.component';

@Component({
  selector: 'app-workspace-users',
  imports: [WorkspaceUsersTableComponent],
  template: `
    <h1 class="text-3xl font-bold mb-4">Users</h1>
    <app-workspace-users-table [userData]="userQuery.data()" [isLoading]="userQuery.isPending() || userQuery.isRefetching()" [teams]="teamsQuery.data()" />
  `
})
export class WorkspaceUsersComponent {
  protected workspaceService = inject(WorkspaceService);
  protected teamService = inject(TeamService);

  userQuery = injectQuery(() => ({
    queryKey: ['workspace', 'users'],
    queryFn: async () => lastValueFrom(combineLatest([this.workspaceService.getUsersWithTeams(), timer(400)]).pipe(map(([user]) => user)))
  }));

  teamsQuery = injectQuery(() => ({
    queryKey: ['workspace', 'teams'],
    queryFn: async () => lastValueFrom(this.teamService.getTeams())
  }));
}
