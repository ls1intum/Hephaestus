import { Component, inject } from '@angular/core';
import { RouterLink, RouterModule } from '@angular/router';
import { LucideAngularModule, Hammer } from 'lucide-angular';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { HlmAvatarModule } from '@spartan-ng/ui-avatar-helm';
import { HlmMenuModule } from '@spartan-ng/ui-menu-helm';
import { HlmIconComponent } from '@spartan-ng/ui-icon-helm';
import { BrnMenuTriggerDirective } from '@spartan-ng/ui-menu-brain';
import { SecurityStore } from '@app/core/security/security-store.service';
import { ThemeSwitcherComponent } from '@app/core/theme/theme-switcher.component';
import { RequestFeatureComponent } from './request-feature/request-feature.component';
import { environment } from 'environments/environment';
import { lucideUser, lucideLogOut, lucideSettings } from '@ng-icons/lucide';
import { provideIcons } from '@ng-icons/core';
import { AiMentorComponent } from './ai-mentor/ai-mentor.component';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  imports: [
    RouterLink,
    RouterModule,
    LucideAngularModule,
    ThemeSwitcherComponent,
    HlmButtonModule,
    RequestFeatureComponent,
    HlmAvatarModule,
    HlmMenuModule,
    BrnMenuTriggerDirective,
    HlmIconComponent,
    AiMentorComponent
  ],
  providers: [
    provideIcons({
      lucideUser,
      lucideLogOut,
      lucideSettings
    })
  ]
})
export class HeaderComponent {
  protected Hammer = Hammer;
  protected version = environment.version;

  securityStore = inject(SecurityStore);
  signedIn = this.securityStore.signedIn;
  user = this.securityStore.loadedUser;

  protected signOut() {
    this.securityStore.signOut();
  }

  protected signIn() {
    if (environment.keycloak.skipLoginPage) {
      const authUrl =
        `${environment.keycloak.url}/realms/${environment.keycloak.realm}/protocol/openid-connect/auth` +
        `?client_id=${encodeURIComponent(environment.keycloak.clientId)}` +
        `&redirect_uri=${encodeURIComponent(environment.clientUrl)}` +
        `&response_type=code` +
        `&scope=openid` +
        `&kc_idp_hint=${encodeURIComponent('github')}`;

      // Redirect the user to the Keycloak GitHub login
      window.location.href = authUrl;
    } else {
      this.securityStore.signIn();
    }
  }
}
