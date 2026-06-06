import * as Sentry from "@sentry/react";
import { createRouter, RouterProvider } from "@tanstack/react-router";
import { PostHogProvider } from "posthog-js/react";
import ReactDOM from "react-dom/client";
import { client } from "@/api/client.gen";
import * as TanstackQuery from "./integrations/tanstack-query/root-provider";
import { routeTree } from "./routeTree.gen";

import "./styles.css";
import { StrictMode, useEffect } from "react";

import environment from "@/environment";
import { AuthProvider, applyStateChangingHeaders, useAuth } from "@/integrations/auth";
import { handlePossibleSessionExpiry } from "@/integrations/auth/sessionExpiry";
import { SessionKeepAlive } from "@/integrations/auth/useSessionKeepAlive";
import { useCookieConsent } from "@/integrations/consent";
import { TanstackDevtools } from "@/integrations/devtools/TanstackDevtools";
import { PostHogIdentity } from "@/integrations/posthog";
import {
	isPosthogEnabled,
	posthogApiHost,
	posthogProjectApiKey,
} from "@/integrations/posthog/config";
import { disableSentry, initSentry } from "@/integrations/sentry";
import { ThemeProvider } from "@/integrations/theme";
import { useImpersonationStore } from "@/stores/impersonation-store";
import reportWebVitals from "./reportWebVitals";

client.setConfig({
	baseUrl: environment.serverUrl,
	// Cookie-session auth (ADR 0017): the __Host-HEPHAESTUS_AT cookie is sent automatically
	// on same-site requests; no Authorization header. credentials:"include" covers the
	// cross-origin dev setup (SPA :4200 → server :8080).
	credentials: "include",
});

// Attach the CSRF double-submit header (X-XSRF-TOKEN from the __Host-XSRF-TOKEN cookie) on every
// state-changing request, plus the impersonation write-allow header when write-mode is on. The pure
// logic lives in applyStateChangingHeaders (unit-tested); the store read stays here at the wiring edge.
// While impersonating, writes are blocked by the server's ImpersonationGuard unless the operator has
// explicitly enabled write-mode (a second confirmation in ImpersonationBanner); the flag is in-memory
// and resets on reload, so it is always a deliberate, fresh opt-in.
client.interceptors.request.use((request) =>
	applyStateChangingHeaders(request, useImpersonationStore.getState().writesEnabled),
);

// Mid-session cookie-expiry handler: when an authenticated in-app request 401s, drop the cached
// identity and redirect to /login with the current path preserved as returnTo. The `GET /user`
// probe and /auth/* are exempt so a logged-out probe never loops (ADR 0017). Uses the SAME shared
// QueryClient the guards/useAuth read.
client.interceptors.response.use((response) => {
	handlePossibleSessionExpiry(response, TanstackQuery.getContext().queryClient);
	return response;
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
	return <RouterProvider router={router} context={{ ...TanstackQuery.getContext(), auth }} />;
}

/**
 * App root. Tracking integrations are consent-gated (ADR 0017 cookie consent):
 *  - Sentry initializes only once error-monitoring consent is granted (and a DSN is configured).
 *  - PostHog is only mounted (PostHogProvider) when analytics consent is granted AND PostHog is
 *    enabled via its env flag. The PostHogProvider already opts out of capturing by default and
 *    PostHogIdentity gates opt-in on the per-user research setting, so consent is an ADDITIONAL,
 *    ANDed gate. When consent is withdrawn the provider unmounts on the next decision.
 */
function Root() {
	const consent = useCookieConsent();
	const errorMonitoring = consent?.errorMonitoring === true;

	// Sentry follows the consent decision as an effect (never as a render side effect): init when
	// error-monitoring consent is granted, tear down when it is withdrawn. Both calls are
	// idempotent, so re-running on any consent change is safe. This mirrors PostHog's
	// mount/unmount gating below — withdrawing consent must actually STOP capture, not just
	// avoid the first init.
	useEffect(() => {
		if (errorMonitoring) {
			initSentry();
		} else {
			disableSentry();
		}
	}, [errorMonitoring]);

	const analyticsEnabled = isPosthogEnabled && consent?.analytics === true;

	const app = (
		<TanstackQuery.Provider>
			<AuthProvider>
				{analyticsEnabled ? <PostHogIdentity /> : null}
				{/* Proactively rotates the access cookie before it expires (only while active), so an
				    active user is never auto-logged-out and an idle session still times out. */}
				<SessionKeepAlive />
				<ThemeProvider defaultTheme="dark" storageKey="theme">
					<WrappedRouterProvider />
					<TanstackDevtools router={router} />
				</ThemeProvider>
			</AuthProvider>
		</TanstackQuery.Provider>
	);

	if (analyticsEnabled) {
		return (
			<PostHogProvider
				apiKey={posthogProjectApiKey}
				options={{
					api_host: posthogApiHost || undefined,
					cross_subdomain_cookie: false,
					opt_out_capturing_by_default: true,
				}}
			>
				{app}
			</PostHogProvider>
		);
	}

	return app;
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
			<Root />
		</StrictMode>,
	);
}

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
