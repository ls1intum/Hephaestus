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
      if (keycloakService.isAuthenticated()) {
        // Try to update the token before sending a request
        try {
          await keycloakService.updateToken(60);
        } catch (refreshError) {
          console.warn('Token refresh failed, will try with existing token:', refreshError);
        }
        
        // Get the current token - even if refresh failed, we might still have a valid token
        const token = keycloakService.getToken();
        
        if (token) {
          // Instead of modifying headers directly, create a new headers object
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
          console.warn('No auth token available for request despite being authenticated');
        }
      } else {
        console.warn('User is not authenticated, not adding token to request');
      }
    } catch (error) {
      console.error('Error in auth interceptor:', error);
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
        // Use a shorter minimum validity time for background refreshes
        const refreshed = await keycloakService.updateToken(120);
        if (refreshed) {
          console.log('Token refreshed in background');
        }
      }
    } catch (error) {
      console.error('Background token refresh failed:', error);
      
      // If there's a serious authentication error, we might need to handle it
      // For example, force a re-authentication if the refresh token is expired
      if (keycloakService.isTokenExpired()) {
        console.warn('Token is expired and refresh failed, user may need to re-authenticate');
      }
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