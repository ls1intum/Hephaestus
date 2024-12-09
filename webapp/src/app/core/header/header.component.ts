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
import { lucideUser, lucideLogOut, lucideSettings } from '@ng-icons/lucide';
import { provideIcons } from '@ng-icons/core';
import { EnvironmentService } from '@app/environment.service';
import { AiMentorComponent } from './ai-mentor/ai-mentor.component';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  standalone: true,
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
  private environmentService = inject(EnvironmentService);

  protected Hammer = Hammer;

  securityStore = inject(SecurityStore);
  signedIn = this.securityStore.signedIn;
  user = this.securityStore.loadedUser;

  protected signOut() {
    this.securityStore.signOut();
  }

  protected get appVersion() {
    return this.environmentService.env.version;
  }

  protected signIn() {
    if (this.environmentService.env.keycloak.skipLoginPage) {
      const authUrl =
        `${this.environmentService.env.keycloak.url}/realms/${this.environmentService.env.keycloak.realm}/protocol/openid-connect/auth` +
        `?client_id=${encodeURIComponent(this.environmentService.env.keycloak.clientId)}` +
        `&redirect_uri=${encodeURIComponent(this.environmentService.env.clientUrl)}` +
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
