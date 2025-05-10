import { Configuration } from '../api/runtime';
import environment from '../environment';
import { authMiddleware } from './apiAuthMiddleware';
import { SessionApi } from '../api/apis/session-api';

/**
 * Creates an API configuration with authentication middleware
 */
export function createApiConfig(): Configuration {
  return new Configuration({
    basePath: environment.serverUrl,
    middleware: [authMiddleware],
  });
}

/**
 * Factory functions for API clients with authentication
 */
export const apiFactory = {
  // Add more API clients here as needed
  sessionApi: () => new SessionApi(createApiConfig()),
};

// Pre-initialized API clients for convenience
export const api = {
  session: apiFactory.sessionApi(),
};