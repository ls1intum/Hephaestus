import { Routes } from '@angular/router';
import { AboutComponent } from '@app/about/about.component';
import { HomeComponent } from '@app/home/home.component';
import { AdminComponent } from '@app/admin/admin.component';
import { AdminGuard } from '@app/core/security/admin.guard';
import { UserProfileComponent } from '@app/user/user-profile.component';
import { SettingsComponent } from '@app/settings/settings.component';
import { ImprintComponent } from '@app/legal/imprint.component';
import { PrivacyComponent } from '@app/legal/privacy.component';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'about', component: AboutComponent },
  {
    path: 'admin',
    component: AdminComponent,
    canActivate: [AdminGuard]
  },
  { path: 'user/:id', component: UserProfileComponent },
  { path: 'settings', component: SettingsComponent },
  { path: 'imprint', component: ImprintComponent },
  { path: 'privacy', component: PrivacyComponent }
];
