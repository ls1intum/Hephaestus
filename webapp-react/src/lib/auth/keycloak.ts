import Keycloak from 'keycloak-js';
import environment from '@/environment';

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
  private profile: UserProfile | undefined;
  private tokenRefreshInterval: number | undefined;
  
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

      if (authenticated) {
        // Setup token refresh mechanism
        this.setupTokenRefresh();
        
        // Load user profile
        try {
          this.profile = (await this.keycloak.loadUserProfile()) as UserProfile;
          this.profile.token = this.keycloak.token || '';
          this.profile.roles = this.keycloak.realmAccess?.roles || [];
        } catch (error) {
          console.error('Failed to load user profile:', error);
        }
      }
      
      return authenticated;
    } catch (error) {
      console.error('Failed to initialize Keycloak:', error);
      return false;
    }
  }

  /**
   * Setup a timer to periodically refresh the token
   */
  private setupTokenRefresh(): void {
    // Clear any existing interval first
    this.clearTokenRefresh();
    
    // Check every minute if the token needs refreshing
    this.tokenRefreshInterval = window.setInterval(() => {
      if (this.isAuthenticated()) {
        this.updateToken(300) // Update when token will expire in less than 5 minutes
          .catch(error => console.warn('Token refresh failed:', error));
      }
    }, 60000); // Check every minute
    
    // Clean up on window unload
    window.addEventListener('unload', this.clearTokenRefresh);
  }
  
  /**
   * Clear the token refresh interval
   */
  private clearTokenRefresh = (): void => {
    if (this.tokenRefreshInterval !== undefined) {
      clearInterval(this.tokenRefreshInterval);
      this.tokenRefreshInterval = undefined;
    }
  };

  /**
   * Check if the user is authenticated
   */
  public isAuthenticated(): boolean {
    return this.keycloak?.authenticated || false;
  }
  
  /**
   * Check if the token is expired
   */
  public isTokenExpired(): boolean {
    if (!this.keycloak || !this.keycloak.tokenParsed) {
      return true;
    }
    
    if (!this.keycloak.tokenParsed.exp) {
      return true;
    }
    
    return this.keycloak.tokenParsed.exp <= Math.floor(Date.now() / 1000);
  }
  
  /**
   * Update token if it's about to expire
   */
  public async updateToken(minValidity = 60): Promise<boolean> {
    if (!this.keycloak) {
      return false;
    }
    
    // Don't refresh if token is still valid for the required time
    if (!this.keycloak.isTokenExpired(minValidity)) {
      return false;
    }
    
    try {
      const refreshed = await this.keycloak.updateToken(minValidity);
      if (refreshed && this.profile) {
        this.profile.token = this.keycloak.token || '';
      }
      return refreshed;
    } catch (error) {
      console.error('Failed to refresh token:', error);
      throw error;
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
   * Get the current user's ID
   */
  public getUserId(): string | undefined {
    return this.keycloak?.tokenParsed?.sub;
  }
  
  /**
   * Get user roles from the token
   */
  public getUserRoles(): string[] {
    return this.keycloak?.realmAccess?.roles || [];
  }
  
  /**
   * Get user profile
   */
  public getUserProfile(): UserProfile | undefined {
    return this.profile;
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
    this.clearTokenRefresh();
    this.initialized = false;
    
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
  
  /**
   * Check if the current user matches the given login
   */
  public isCurrentUser(login?: string): boolean {
    if (!login) return false;
    const username = this.keycloak?.tokenParsed?.preferred_username;
    return username?.toLowerCase() === login.toLowerCase();
  }
}

// Export a singleton instance
const keycloakService = new KeycloakService();
export default keycloakService;