import { client } from './client.gen';
import environment from '../lib/environment';
import keycloakService from '../lib/auth/keycloak';

/**
 * Initializes the Hey API client with base URL and authentication
 */
export function initializeApiClient() {
  // Configure the client with the base URL
  client.setConfig({
    baseUrl: environment.serverUrl,
    // Add authentication through config instead of interceptors
    auth: () => {
      const token = keycloakService.getToken();
      return token ? `Bearer ${token}` : '';
    }
  });
  
  return client;
}

/**
 * Setup a pre-request handler that refreshes the token if needed
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