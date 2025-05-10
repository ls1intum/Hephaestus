import { client } from '../../api/client.gen';
import keycloakService from './keycloak';

// Track if interceptors are already set up
let authInterceptorSet = false;
let tokenRefreshIntervalId: number | undefined = undefined;

/**
 * Sets up the authentication function for API requests.
 * This adds the Authorization header with Bearer token to each request.
 */
export function setupAuthFunction() {
  // Prevent duplicate interceptors
  if (authInterceptorSet) {
    console.log('Auth interceptor already set up, skipping');
    return;
  }
  
  console.log('Setting up API auth interceptor');
  
  // Add request interceptor to inject auth token
  client.interceptors.request.use(async (request) => {
    try {
      // Always try to update the token before sending a request
      await keycloakService.updateToken();
      
      // Get the current token
      const token = keycloakService.getToken();
      
      if (token) {
        // Instead of modifying headers directly, create a new headers object
        // and assign it back to the request in a way that respects readonly properties
        const newHeaders = {
          ...request.headers,
          Authorization: `Bearer ${token}`
        };
        
        // Replace the entire request object with a new one containing updated headers
        Object.assign(request, {
          headers: newHeaders
        });
        
        console.log('Added auth token to request');
      } else {
        console.warn('No auth token available for request');
      }
    } catch (error) {
      console.error('Failed to update token for request:', error);
    }
    
    return request;
  });
  
  authInterceptorSet = true;
}

/**
 * Sets up a token refresh mechanism that periodically checks
 * if the token needs to be refreshed.
 */
export function setupTokenRefresh() {
  // Clear any existing interval first
  if (tokenRefreshIntervalId !== undefined) {
    console.log('Token refresh already set up, clearing previous interval');
    clearInterval(tokenRefreshIntervalId);
  }
  
  console.log('Setting up token refresh mechanism');
  
  // Check token every 30 seconds
  tokenRefreshIntervalId = window.setInterval(async () => {
    try {
      if (keycloakService.isAuthenticated()) {
        const refreshed = await keycloakService.updateToken();
        if (refreshed) {
          console.log('Token refreshed in background');
        }
      }
    } catch (error) {
      console.error('Background token refresh failed:', error);
    }
  }, 30000);
  
  // Clean up interval when window is unloaded
  window.addEventListener('unload', () => {
    if (tokenRefreshIntervalId !== undefined) {
      clearInterval(tokenRefreshIntervalId);
      tokenRefreshIntervalId = undefined;
    }
  });
}

/**
 * Resets the auth configuration for testing or after logout
 */
export function resetAuthConfig() {
  authInterceptorSet = false;
  if (tokenRefreshIntervalId !== undefined) {
    clearInterval(tokenRefreshIntervalId);
    tokenRefreshIntervalId = undefined;
  }
}