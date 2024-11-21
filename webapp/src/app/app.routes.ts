import { Routes } from '@angular/router';
import { AboutComponent } from '@app/about/about.component';
import { HomeComponent } from '@app/home/home.component';
import { AdminComponent } from '@app/admin/admin.component';
import { UserProfileComponent } from '@app/user/user-profile.component';
import { SettingsComponent } from '@app/settings/settings.component';
import { ImprintComponent } from '@app/legal/imprint.component';
import { PrivacyComponent } from '@app/legal/privacy.component';
import { AdminGuard } from '@app/core/security/admin.guard';
import { AuthGuard } from '@app/core/security/auth.guard';

export const routes: Routes = [
  // Public routes
  { path: 'about', component: AboutComponent },
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
      { path: 'admin', component: AdminComponent, canActivate: [AdminGuard] }
    ]
  }
];
