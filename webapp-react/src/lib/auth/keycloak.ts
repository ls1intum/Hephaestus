import Keycloak from 'keycloak-js';
import type { KeycloakLoginOptions } from 'keycloak-js';
import environment from '../environment';

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

class KeycloakService {
  private _keycloak: Keycloak | undefined;
  profile: UserProfile | undefined;
  
  get keycloak(): Keycloak {
    if (!this._keycloak) {
      this._keycloak = new Keycloak({
        url: environment.keycloak.url,
        realm: environment.keycloak.realm,
        clientId: environment.keycloak.clientId,
      });
    }
    return this._keycloak;
  }

  async init(): Promise<boolean> {
    try {
      // Match exactly the Angular implementation's init options
      const authenticated = await this.keycloak.init({
        onLoad: 'check-sso',
        silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html',
        silentCheckSsoFallback: false,
        checkLoginIframe: false // Disable login iframe check to avoid CORS issues
      });

      if (!authenticated) {
        return authenticated;
      }
      
      try {
        // Load user profile similar to Angular implementation
        this.profile = await this.keycloak.loadUserInfo() as unknown as UserProfile;
        if (this.profile) {
          this.profile.token = this.keycloak.token || '';
          this.profile.roles = this.keycloak.realmAccess?.roles || [];
        }
      } catch (error) {
        console.warn('Failed to load user info, but continuing', error);
        // Create a minimal profile if loadUserInfo fails
        this.profile = {
          email: '',
          email_verified: false,
          given_name: '',
          family_name: '',
          name: '',
          preferred_username: this.keycloak.tokenParsed?.preferred_username || '',
          realmAccess: { roles: this.keycloak.realmAccess?.roles || [] },
          roles: this.keycloak.realmAccess?.roles || [],
          sub: this.keycloak.tokenParsed?.sub || '',
          token: this.keycloak.token || ''
        };
      }

      return true;
    } catch (error) {
      console.error('Failed to initialize Keycloak', error);
      return false;
    }
  }

  async updateToken(minValidity = 60): Promise<boolean> {
    try {
      if (!this.keycloak.isTokenExpired(minValidity)) {
        return false;
      }
      
      // Try to refresh token
      const refreshed = await this.keycloak.updateToken(minValidity);
      if (refreshed && this.profile) {
        this.profile.token = this.keycloak.token || '';
      }
      return refreshed;
    } catch (error) {
      console.error('Failed to refresh token:', error);
      return false;
    }
  }

  login(options?: KeycloakLoginOptions): Promise<void> {
    // Ensure redirectUri is explicitly set to match what's registered in Keycloak
    const loginOptions: KeycloakLoginOptions = {
      redirectUri: environment.clientUrl,
      ...options
    };
    
    // Add idpHint if skipLoginPage is enabled
    if (environment.keycloak.skipLoginPage) {
      loginOptions.idpHint = 'github';
    }
      
    return this.keycloak.login(loginOptions);
  }

  logout(): Promise<void> {
    return this.keycloak.logout({ redirectUri: environment.clientUrl });
  }

  isAuthenticated(): boolean {
    return !!this.keycloak.authenticated;
  }

  getToken(): string | undefined {
    return this.keycloak.token;
  }

  getUsername(): string | undefined {
    return this.profile?.preferred_username ?? this.keycloak.tokenParsed?.preferred_username;
  }
  
  getUserRoles(): string[] {
    return this.profile?.roles || this.keycloak.realmAccess?.roles || [];
  }

  hasRole(role: string): boolean {
    return this.getUserRoles().includes(role);
  }
}

const keycloakService = new KeycloakService();
export default keycloakService;