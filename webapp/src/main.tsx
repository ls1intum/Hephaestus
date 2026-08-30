import * as Sentry from "@sentry/react";
import { createRouter, RouterProvider } from "@tanstack/react-router";
import { StrictMode, useEffect } from "react";
import ReactDOM from "react-dom/client";

import { client } from "@/api/client.gen";
import environment from "@/environment";
import { RouteError } from "@/integrations/sentry/RouteError";

import "./styles.css";

import { AuthProvider, applyStateChangingHeaders, useAuth } from "@/integrations/auth";
import { handlePossibleSessionExpiry } from "@/integrations/auth/session-expiry";
import { SessionKeepAlive } from "@/integrations/auth/use-session-keep-alive";
import { useCookieConsent } from "@/integrations/consent";
import { TanstackDevtools } from "@/integrations/devtools/TanstackDevtools";
import { disableSentry, initSentry } from "@/integrations/sentry";
import { ThemeProvider } from "@/integrations/theme";
import { useImpersonationStore } from "@/stores/impersonation-store";

import * as TanstackQuery from "./integrations/tanstack-query/root-provider";
import { routeTree } from "./routeTree.gen";

// No default request timeout, deliberately: it would have to clear the slowest honest response (a
// workspace purge is unbounded by design), and aborting a mutation does not abort the server — it
// would report a write the server went on to apply as a failure.
client.setConfig({
	baseUrl: environment.serverUrl,
	// Cookie-session auth (ADR 0017): the __Host-HEPHAESTUS_AT cookie is sent automatically
	// on same-site requests; no Authorization header. credentials:"include" covers the
	// cross-origin dev setup (SPA :4200 → server :8080).
	credentials: "include",
});

// Register the web-app manifest from here rather than an inline <script> in index.html: the
// deployed Content-Security-Policy is `script-src 'self'` (webapp/docker/security-headers.conf and
// the Traefik edge middleware), which blocks inline scripts. Browsers process a manifest <link>
// whenever it is added, so doing it from the bundle loses nothing.
{
	const manifestLink = document.createElement("link");
	manifestLink.rel = "manifest";
	manifestLink.href =
		window.location.hostname === "localhost" ? "/manifest-dev.json" : "/manifest.json";
	document.head.appendChild(manifestLink);
}

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

const router = createRouter({
	routeTree,
	context: {
		...TanstackQuery.getContext(),
		auth: undefined,
	},
	defaultPreload: "intent",
	scrollRestoration: true,
	// `index.html` sets `scroll-smooth`, so an unspecified behaviour resolves to `auto` and CSSOM-View
	// makes every restore an *animated* scroll — one that a second write can abort halfway.
	scrollRestorationBehavior: "instant",
	defaultStructuralSharing: true,
	defaultPreloadStaleTime: 0,
	defaultErrorComponent: RouteError,
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

function Root() {
	const consent = useCookieConsent();
	const errorMonitoring = consent?.errorMonitoring === true;

	useEffect(() => {
		if (errorMonitoring) {
			initSentry();
		} else {
			disableSentry();
		}
	}, [errorMonitoring]);

	return (
		<TanstackQuery.Provider>
			<AuthProvider>
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
}

const rootElement = document.getElementById("app");
if (rootElement && !rootElement.innerHTML) {
	const root = ReactDOM.createRoot(rootElement, {
		onUncaughtError: Sentry.reactErrorHandler((error, errorInfo) => {
			// oxlint-disable-next-line no-console -- The custom handler replaces React's console report.
			console.warn("Uncaught error", error, errorInfo.componentStack);
		}),
		onRecoverableError: Sentry.reactErrorHandler(),
	});
	root.render(
		<StrictMode>
			<Root />
		</StrictMode>,
	);
}
