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
    // Load user profile
    this.profile = (await this.keycloak.loadUserInfo()) as unknown as UserProfile;
    this.profile.token = this.keycloak.token || '';
    this.profile.roles = this.keycloak.realmAccess?.roles || [];

    console.log('token', this.keycloak.token);

    return true;
  }

  /**
   * Update access token if it is about to expire or has expired
   * This is independent from the silent check sso or refresh token validity.
   * @returns
   */
  async updateToken() {
    if (!this.keycloak.isTokenExpired(60)) {
      return false;
    }
    try {
      // Try to refresh token
      const refreshed = await this.keycloak.updateToken(60);
      if (refreshed) {
        this.profile!.token = this.keycloak.token || '';
      }
      return refreshed;
    } catch (error) {
      console.error('Failed to refresh token:', error);
      // Redirect to login if refresh fails
      await this.keycloak.login();
      return false;
    }
  }

  login() {
    return this.keycloak.login();
  }

  logout() {
    return this.keycloak.logout({ redirectUri: environment.clientUrl });
  }
}
