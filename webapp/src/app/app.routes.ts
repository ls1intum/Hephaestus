import { Routes } from '@angular/router';
import { AboutComponent } from '@app/about/about.component';
import { HomeComponent } from '@app/home/home.component';
import { WorkspaceComponent } from '@app/workspace/workspace.component';
import { UserProfileComponent } from '@app/user/user-profile.component';
import { WorkspaceUsersComponent } from './workspace/users/users.component';
import { WorkspaceLayoutComponent } from './workspace/layout.component';
import { WorkspaceTeamsComponent } from './workspace/teams/teams.component';
import { SettingsComponent } from '@app/settings/settings.component';
import { ImprintComponent } from '@app/legal/imprint.component';
import { PrivacyComponent } from '@app/legal/privacy.component';
import { AdminGuard } from '@app/core/security/admin.guard';
import { AuthGuard } from '@app/core/security/auth.guard';
import {
  ActivityDashboardComponent
} from '@app/home/activity-dashboard/activity-dashboard/activity-dashboard.component';

export const routes: Routes = [
  // Public routes
  { path: 'about', component: AboutComponent },
  {
    path: 'workspace',
    component: WorkspaceLayoutComponent,
    canActivate: [AdminGuard],
    children: [
      {
        path: '',
        component: WorkspaceComponent
      },
      {
        path: 'users',
        component: WorkspaceUsersComponent
      },
      {
        path: 'teams',
        component: WorkspaceTeamsComponent
      }
    ]
  },
  { path: 'user/:id', component: UserProfileComponent },
  { path: 'settings', component: SettingsComponent },
  { path: 'imprint', component: ImprintComponent },
  { path: 'privacy', component: PrivacyComponent },

  // Protected routes
  {
    path: '',
    canActivate: [AuthGuard],
    children: [
      { path: '', component: HomeComponent },
      { path: 'user/:id', component: UserProfileComponent },
      { path: 'settings', component: SettingsComponent },
      { path: 'workspace', component: WorkspaceComponent, canActivate: [AdminGuard] }
    ]
  }
  { path: 'user/:id', component: UserProfileComponent },
  { path: 'activity/:id', component: ActivityDashboardComponent }
];
