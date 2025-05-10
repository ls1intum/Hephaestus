import { client } from './client.gen';
import environment from '../lib/environment';
import { setupAuthFunction, setupTokenRefresh } from '../lib/auth/hey-api-auth';

/**
 * Initialize the API client with base configuration
 * Note: Authentication is handled separately in lib/auth/hey-api-auth.ts
 */
export function initApiClient() {
  console.log('Initializing API client with base URL:', environment.serverUrl);
  
  // Set base URL for API requests
  client.setConfig({
    baseUrl: environment.serverUrl
  });

  // Add custom request interceptors
  client.interceptors.request.use(request => {
    console.log(`API request to ${request.url}`);
    return request;
  });

  // Initialize auth functions
  setupAuthFunction();
  setupTokenRefresh();

  return client;
}

// Add alias export to match the import in main.tsx
export const initializeApiClient = initApiClient;

// Remove automatic initialization
// initApiClient(); - This was causing double initialization