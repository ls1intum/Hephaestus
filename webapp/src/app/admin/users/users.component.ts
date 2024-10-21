import { Component, inject } from '@angular/core';
import { combineLatest, lastValueFrom, map, timer } from 'rxjs';
import { AdminService, TeamService } from '@app/core/modules/openapi';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { AdminUsersTableComponent } from './table/users-table.component';
import { RouterLink } from '@angular/router';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [RouterLink, HlmButtonModule, AdminUsersTableComponent],
  templateUrl: './users.component.html'
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
