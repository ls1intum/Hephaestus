import Keycloak from 'keycloak-js';
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
  _keycloak: Keycloak | undefined;
  profile: UserProfile | undefined;
  
  get keycloak(): Keycloak {
    if (!this._keycloak) {
      this._keycloak = new Keycloak({
        url: environment.keycloak.url,
        realm: environment.keycloak.realm,
        clientId: environment.keycloak.clientId
      });
    }
    return this._keycloak;
  }

  async init(): Promise<boolean> {
    try {
      console.log('Initializing Keycloak...');
      // Using exact configuration from Angular implementation
      const authenticated = await this.keycloak.init({
        onLoad: 'check-sso',
        silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html',
        silentCheckSsoFallback: false
      });

      console.log('Keycloak initialization result:', authenticated);

      if (!authenticated) {
        console.log('User is not authenticated');
        return authenticated;
      }
      
      console.log('User is authenticated. Loading profile...');
      // Load user profile - exactly like Angular
      this.profile = await this.keycloak.loadUserInfo() as unknown as UserProfile;
      this.profile.token = this.keycloak.token || '';
      this.profile.roles = this.keycloak.realmAccess?.roles || [];
      
      console.log('User profile loaded:', {
        username: this.profile.preferred_username,
        roles: this.profile.roles,
        tokenExpiry: this.keycloak.tokenParsed?.exp 
          ? new Date(this.keycloak.tokenParsed.exp * 1000).toISOString()
          : 'unknown'
      });

      return true;
    } catch (error) {
      console.error('Failed to initialize Keycloak', error);
      return false;
    }
  }

  async updateToken(): Promise<boolean> {
    if (!this.keycloak.isTokenExpired(60)) {
      return false;
    }
    
    try {
      // Try to refresh token - exactly like Angular
      const refreshed = await this.keycloak.updateToken(60);
      if (refreshed && this.profile) {
        this.profile.token = this.keycloak.token || '';
      }
      return refreshed;
    } catch (error) {
      console.error('Failed to refresh token:', error);
      // Redirect to login if refresh fails
      await this.keycloak.login();
      return false;
    }
  }

  login(): Promise<void> {
    // Using exact implementation from Angular
    return this.keycloak.login();
  }

  logout(): Promise<void> {
    // Using exact implementation from Angular
    return this.keycloak.logout({ redirectUri: environment.clientUrl });
  }

  isAuthenticated(): boolean {
    const authenticated = !!this.keycloak.authenticated;
    console.log('isAuthenticated check:', authenticated, 'Token exists:', !!this.keycloak.token);
    return authenticated;
  }

  getToken(): string | undefined {
    return this.keycloak.token;
  }

  getUsername(): string | undefined {
    return this.profile?.preferred_username;
  }
  
  getUserRoles(): string[] {
    return this.profile?.roles || [];
  }

  hasRole(role: string): boolean {
    return this.getUserRoles().includes(role);
  }
}

const keycloakService = new KeycloakService();
export default keycloakService;