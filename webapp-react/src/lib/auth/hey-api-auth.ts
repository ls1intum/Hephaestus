import { client } from '../../api/client.gen';
import keycloakService from './keycloak';
import environment from '../environment';

/**
 * Sets up auth function for the Hey API client
 * This provides authentication token for API requests
 */
export function setupAuthFunction() {
  // Use auth function in config 
  client.setConfig({
    baseUrl: environment.serverUrl,
    auth: () => {
      const token = keycloakService.getToken();
      return token ? `Bearer ${token}` : '';
    }
  });
}

/**
 * Setup a pre-request interceptor that refreshes the token if needed
 */
export function setupTokenRefresh() {
  client.interceptors.request.use(async (request) => {
    try {
      // Only try to refresh if we're authenticated
      if (keycloakService.isAuthenticated()) {
        await keycloakService.updateToken();
      }
    } catch (error) {
      console.error('Token refresh error:', error);
    }
    
    // Return the unchanged request - authentication headers will be added by config.auth
    return request;
  });
}