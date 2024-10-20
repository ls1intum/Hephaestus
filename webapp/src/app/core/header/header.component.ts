import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule, Hammer } from 'lucide-angular';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { SecurityStore } from '@app/core/security/security-store.service';
import { ThemeSwitcherComponent } from '@app/core/theme/theme-switcher.component';
import { RequestFeatureComponent } from './request-feature/request-feature.component';
import { environment } from 'environments/environment';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, ThemeSwitcherComponent, HlmButtonModule, RequestFeatureComponent]
})
export class HeaderComponent {
  protected Hammer = Hammer;

  securityStore = inject(SecurityStore);
  signedIn = this.securityStore.signedIn;

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
