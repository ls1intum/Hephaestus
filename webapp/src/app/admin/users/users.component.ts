import { Component, inject } from '@angular/core';
import { combineLatest, lastValueFrom, map, timer } from 'rxjs';
import { AdminService, TeamService } from '@app/core/modules/openapi';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { AdminUsersTableComponent } from './table/users-table.component';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [RouterLink, AdminUsersTableComponent],
  template: `
    <h1 class="text-3xl font-bold mb-4">Users</h1>
    <app-admin-users-table [userData]="userQuery.data()" [isLoading]="userQuery.isPending() || userQuery.isRefetching()" [teams]="teamsQuery.data()" />
  `
})
export class AdminUsersComponent {
  protected adminService = inject(AdminService);
  protected teamService = inject(TeamService);

  userQuery = injectQuery(() => ({
    queryKey: ['admin', 'users'],
    queryFn: async () => lastValueFrom(combineLatest([this.adminService.getUsersAsAdmin(), timer(400)]).pipe(map(([user]) => user)))
  }));

  teamsQuery = injectQuery(() => ({
    queryKey: ['admin', 'teams'],
    queryFn: async () => lastValueFrom(this.teamService.getTeams())
  }));
}
