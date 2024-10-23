import { Routes } from '@angular/router';
import { AboutComponent } from '@app/about/about.component';
import { HomeComponent } from '@app/home/home.component';
import { AdminComponent } from '@app/admin/admin.component';
import { AdminGuard } from '@app/core/security/admin.guard';
import { UserProfileComponent } from '@app/user/user-profile.component';
import { AdminUsersComponent } from './admin/users/users.component';
import { AdminLayoutComponent } from './admin/layout.component';
import { AdminTeamsComponent } from './admin/teams/teams.component';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'about', component: AboutComponent },
  {
    path: 'admin',
    component: AdminLayoutComponent,
    canActivate: [AdminGuard],
    children: [
      {
        path: '',
        component: AdminComponent
      },
      {
        path: 'users',
        component: AdminUsersComponent
      },
      {
        path: 'teams',
        component: AdminTeamsComponent
      }
    ]
  },
  { path: 'user/:id', component: UserProfileComponent }
];
