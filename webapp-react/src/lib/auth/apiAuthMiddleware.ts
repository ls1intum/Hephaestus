import type { Middleware, RequestContext, ResponseContext } from '../../api/runtime';
import keycloakService from './keycloak';

/**
 * Middleware for handling authentication in API requests
 * - Adds the Authorization header with the access token
 * - Refreshes the token if needed before making requests
 */
export const authMiddleware: Middleware = {
  pre: async (context: RequestContext) => {
    try {
      // Try to refresh token if needed (60 seconds minimum validity)
      if (keycloakService.isAuthenticated()) {
        await keycloakService.updateToken();
        
        const token = keycloakService.getToken();
        if (token) {
          // Add Authorization header if token exists
          context.init.headers = {
            ...context.init.headers,
            'Authorization': `Bearer ${token}`
          };
        }
      }
      
      return {
        url: context.url,
        init: context.init
      };
    } catch (error) {
      console.error('Auth middleware error:', error);
      // Continue with request even if token refresh fails
      return {
        url: context.url,
        init: context.init
      };
    }
  },
  
  post: async (context: ResponseContext) => {
    if (context.response.status === 401 || context.response.status === 403) {
      console.warn('Authentication error in API request');
      
      // If we get a 401/403, we might need to re-authenticate
      if (keycloakService.isAuthenticated()) {
        try {
          await keycloakService.updateToken(0); // Force token refresh
        } catch (error) {
          // Token refresh failed, will redirect to login
          console.error('Token refresh failed after 401/403', error);
        }
      }
    }
    
    return context.response;
  }
};