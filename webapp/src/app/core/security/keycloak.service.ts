import { Injectable } from '@angular/core';
import { environment } from 'environments/environment';
import Keycloak from 'keycloak-js';

export interface UserProfile {
  email: string;
  email_verified: boolean;
  given_name: string;
  family_name: string;
  name: string;
  preferred_username: string;
  realmAccess: { roles: string[] };
  roles: string[];
  sub: string;
  token: string;
}

@Injectable({ providedIn: 'root' })
export class KeycloakService {
  _keycloak: Keycloak | undefined;
  profile: UserProfile | undefined;
  tokenRefreshInterval = 60; // in seconds

  get keycloak() {
    if (!this._keycloak) {
      this._keycloak = new Keycloak({
        url: environment.keycloak.url,
        realm: environment.keycloak.realm,
        clientId: environment.keycloak.clientId
      });
    }
    return this._keycloak;
  }

  async init() {
    const authenticated = await this.keycloak.init({
      onLoad: 'check-sso',
      silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html',
      silentCheckSsoFallback: false
    });

    if (!authenticated) {
      return authenticated;
    }
    this.profile = (await this.keycloak.loadUserInfo()) as unknown as UserProfile;
    this.profile.token = this.keycloak.token || '';
    this.profile.roles = this.keycloak.realmAccess?.roles || [];

    // Check refresh token expiry
    setInterval(() => {
      this.updateToken();
    }, this.tokenRefreshInterval * 1000);

    return true;
  }

  private async updateToken() {
    try {
      // Try to refresh token if it's about to expire
      const refreshed = await this.keycloak.updateToken(this.tokenRefreshInterval + 10);
      if (refreshed) {
        this.profile!.token = this.keycloak.token || '';
      }
    } catch (error) {
      console.error('Failed to refresh token:', error);
      // Redirect to login if refresh fails
      await this.keycloak.login();
    }
  }

  login() {
    return this.keycloak.login();
  }

  logout() {
    return this.keycloak.logout({ redirectUri: environment.clientUrl });
  }
}
