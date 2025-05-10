import { RouterProvider, createRouter } from "@tanstack/react-router";
import ReactDOM from "react-dom/client";

import * as TanstackQuery from "./integrations/tanstack-query/root-provider";

import { routeTree } from "./routeTree.gen";
import { client } from '@/api/client.gen';

import "./styles.css";
import reportWebVitals from "./reportWebVitals.ts";

import { AuthProvider } from "./lib/auth/AuthContext";
import environment from "./environment/index.ts";
import keycloakService from "./lib/auth/keycloak.ts";

client.setConfig({
  baseUrl: environment.serverUrl,
});

// Add request interceptor to handle authentication
client.interceptors.request.use(async (request) => {
  // Skip authentication for public endpoints
  if (request.url?.includes('/public/')) {
    return request;
  }
  
  // Only try to update token if authenticated
  if (keycloakService.isAuthenticated()) {
    try {
			// Check if token needs to be refreshed (within 60 seconds of expiration)
      await keycloakService.updateToken(60);
    } catch (error) {
      console.error('Token refresh failed in interceptor:', error);
    }
  }
  
  // Add token to request header if available
  const token = keycloakService.getToken();
  if (token) {
    request.headers.set('Authorization', `Bearer ${token}`);
  }
  
  return request;
});

// Create a new router instance
const router = createRouter({
	routeTree,
	context: {
		...TanstackQuery.getContext(),
	},
	defaultPreload: "intent",
	scrollRestoration: true,
	defaultStructuralSharing: true,
	defaultPreloadStaleTime: 0,
});

// Register the router instance for type safety
declare module "@tanstack/react-router" {
	interface Register {
		router: typeof router;
	}
}

// Render the app
const rootElement = document.getElementById("app");
if (rootElement && !rootElement.innerHTML) {
	const root = ReactDOM.createRoot(rootElement);
	root.render(
		// Removed StrictMode wrapper to prevent double rendering in development
		<TanstackQuery.Provider>
			<AuthProvider>
				<RouterProvider router={router} />
			</AuthProvider>
		</TanstackQuery.Provider>
	);
}

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
