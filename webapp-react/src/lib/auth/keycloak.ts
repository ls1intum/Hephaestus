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
  private keycloak: Keycloak | null = null;
  private initialized = false;
  
  /**
   * Initialize the Keycloak instance
   */
  public async init(): Promise<boolean> {
    if (this.initialized) {
      return this.keycloak?.authenticated || false;
    }
    
    try {
      this.keycloak = new Keycloak({
        url: environment.keycloak.url,
        realm: environment.keycloak.realm,
        clientId: environment.keycloak.clientId
      });
      
      const authenticated = await this.keycloak.init({
        onLoad: 'check-sso',
        silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html',
        checkLoginIframe: false
      });
      
      this.initialized = true;
      console.log('Keycloak initialized, authenticated:', authenticated);
      
      return authenticated;
    } catch (error) {
      console.error('Failed to initialize Keycloak:', error);
      return false;
    }
  }
  
  /**
   * Check if the user is authenticated
   */
  public isAuthenticated(): boolean {
    return this.keycloak?.authenticated || false;
  }
  
  /**
   * Update token if it's about to expire
   */
  public async updateToken(minValidity = 60): Promise<boolean> {
    if (!this.keycloak) {
      console.warn('Keycloak not initialized when updateToken called');
      return false;
    }
    
    try {
      return await this.keycloak.updateToken(minValidity);
    } catch (error) {
      console.error('Failed to refresh token:', error);
      return false;
    }
  }
  
  /**
   * Get the current auth token
   */
  public getToken(): string | undefined {
    return this.keycloak?.token;
  }
  
  /**
   * Get the current user's username
   */
  public getUsername(): string {
    return this.keycloak?.tokenParsed?.preferred_username || '';
  }
  
  /**
   * Get user roles from the token
   */
  public getUserRoles(): string[] {
    return this.keycloak?.realmAccess?.roles || [];
  }
  
  /**
   * Redirect to the login page
   */
  public login(): Promise<void> {
    return this.keycloak?.login() || Promise.resolve();
  }
  
  /**
   * Logout the current user
   */
  public logout(): Promise<void> {
    return this.keycloak?.logout({
      redirectUri: window.location.origin
    }) || Promise.resolve();
  }
  
  /**
   * Check if the user has the specified role
   */
  public hasRole(role: string): boolean {
    return this.keycloak?.hasRealmRole(role) || false;
  }
}

// Export a singleton instance
const keycloakService = new KeycloakService();
export default keycloakService;