import { Configuration } from '../api/runtime';
import environment from '../environment';
import { authMiddleware } from './apiAuthMiddleware';

/**
 * Creates a configuration object for API clients that includes authentication
 * middleware and other necessary settings
 */
export function configureApi(): Configuration {
  return new Configuration({
    basePath: environment.serverUrl,
    middleware: [authMiddleware],
  });
}

/**
 * Use this function to configure any API client with authentication
 * This allows you to use the generated API clients with authentication
 * without modifying the generated code
 * 
 * Example:
 * ```
 * import { UsersApi } from '../api/apis';
 * import { withAuth } from './configureApi';
 * 
 * const usersApi = withAuth(new UsersApi());
 * ```
 */
export function withAuth<T extends { withMiddleware: Function }>(apiClient: T): T {
  return apiClient.withMiddleware(authMiddleware) as T;
}