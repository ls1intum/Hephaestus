import * as Sentry from "@sentry/react";
import { createRouter, RouterProvider } from "@tanstack/react-router";
import { PostHogProvider } from "posthog-js/react";
import ReactDOM from "react-dom/client";

import * as TanstackQuery from "./integrations/tanstack-query/root-provider";
import "./integrations/sentry";

import { client } from "@/api/client.gen";
import { routeTree } from "./routeTree.gen";

import "./styles.css";
import { StrictMode } from "react";

import environment from "@/environment";
import { AuthProvider, keycloakService, useAuth } from "@/integrations/auth";
import { PostHogIdentity } from "@/integrations/posthog";
import { isPosthogEnabled, posthogApiHost, posthogProjectApiKey } from "@/integrations/posthog/config";
import { ThemeProvider } from "@/integrations/theme";
import reportWebVitals from "./reportWebVitals";

client.setConfig({
	baseUrl: environment.serverUrl,
});

// Add request interceptor to handle authentication
client.interceptors.request.use(async (request) => {
	// Skip authentication for public endpoints
	if (request.url?.includes("/public/")) {
		return request;
	}

	// Only try to update token if authenticated
	if (keycloakService.isAuthenticated()) {
		try {
			// Check if token needs to be refreshed (within 60 seconds of expiration)
			await keycloakService.updateToken(60);
		} catch (error) {
			console.error("Token refresh failed in interceptor:", error);
		}
	}

	// Add token to request header if available
	const token = keycloakService.getToken();
	if (token) {
		request.headers.set("Authorization", `Bearer ${token}`);
	}

	return request;
});

// Create a new router instance
const router = createRouter({
	routeTree,
	context: {
		...TanstackQuery.getContext(),
		auth: undefined,
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

function WrappedRouterProvider() {
	const auth = useAuth();
	return (
		<RouterProvider
			router={router}
			context={{ ...TanstackQuery.getContext(), auth }}
		/>
	);
}

// Render the app
const rootElement = document.getElementById("app");
if (rootElement && !rootElement.innerHTML) {
	const root = ReactDOM.createRoot(rootElement, {
		// Callback called when an error is thrown and not caught by an ErrorBoundary.
		onUncaughtError: Sentry.reactErrorHandler((error, errorInfo) => {
			console.warn("Uncaught error", error, errorInfo.componentStack);
		}),
		// Callback called when React catches an error in an ErrorBoundary.
		onCaughtError: Sentry.reactErrorHandler(),
		// Callback called when React automatically recovers from errors.
		onRecoverableError: Sentry.reactErrorHandler(),
	});
	root.render(
		<StrictMode>
			{isPosthogEnabled ? (
				<PostHogProvider
					apiKey={posthogProjectApiKey}
					options={{
						api_host: posthogApiHost || undefined,
						cross_subdomain_cookie: false,
						opt_out_capturing_by_default: true,
					}}
				>
					<TanstackQuery.Provider>
						<AuthProvider>
							<PostHogIdentity />
							<ThemeProvider defaultTheme="dark" storageKey="theme">
								<WrappedRouterProvider />
							</ThemeProvider>
						</AuthProvider>
					</TanstackQuery.Provider>
				</PostHogProvider>
			) : (
				<TanstackQuery.Provider>
					<AuthProvider>
						<ThemeProvider defaultTheme="dark" storageKey="theme">
							<WrappedRouterProvider />
						</ThemeProvider>
					</AuthProvider>
				</TanstackQuery.Provider>
			)}
		</StrictMode>,
	);
}

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
