import { client } from '../../api/client.gen';
import keycloakService from './keycloak';

/**
 * Adds authentication headers to the API client
 * 
 * @returns A function that returns the authorization header with the current token
 */
export function getAuthHeader(): () => string {
  return () => {
    const token = keycloakService.getToken();
    return token ? `Bearer ${token}` : '';
  };
}

/**
 * Creates a configured client with auth headers
 * 
 * This is a wrapper for the Hey API client to use in components
 */
export function createAuthenticatedClient() {
  // Configure the authorization header function
  client.setConfig({
    auth: getAuthHeader()
  });
  
  return client;
}

/**
 * Helper function to get a client that will automatically include auth tokens
 */
export function useApiClient() {
  return client;
}