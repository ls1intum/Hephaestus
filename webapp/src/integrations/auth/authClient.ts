import type { CurrentUserView } from "@/api/types.gen";
import environment from "@/environment";
import { safeReturnTo } from "@/integrations/auth/guard";

/**
 * Cookie-session auth client (ADR 0017).
 *
 * The browser holds a `__Host-HEPHAESTUS_AT` HttpOnly cookie minted by the server
 * after the OAuth login dance — the SPA never sees a token. Identity is read from
 * `GET /user`; login/link are top-level redirects to the server's `/auth/login`
 * kickoff; logout is a CSRF-protected POST.
 */

/**
 * Shape returned by the server's `GET /user`. The generated `CurrentUserView` types
 * every field as optional; here we require the handful the client always relies on and
 * keep the rest optional so callers don't need to null-check the universe.
 */
export type CurrentUser = Required<
	Pick<CurrentUserView, "id" | "displayName" | "appRole" | "status">
> &
	Pick<
		CurrentUserView,
		| "primaryEmail"
		| "impersonating"
		| "impersonatorId"
		| "username"
		| "avatarUrl"
		| "profileUrl"
		| "identityProvider"
		| "gitProviderId"
		| "hasGitLabIdentity"
		| "roles"
	>;

/** Profile shape consumed by `useAuth().userProfile`. */
export interface UserProfile {
	id: string;
	username: string;
	firstName: string;
	lastName: string;
	email: string;
	name: string;
	roles: string[];
	githubId?: string;
	gitlabId?: string;
	identityProvider?: string;
	/** SCM instances this account has an active identity on (for instance-scoped gating). */
	linkedProviders: Array<{ type: string; serverUrl?: string }>;
}

const serverUrl = () => environment.serverUrl.replace(/\/$/, "");

/** Read a cookie value by name (used for the CSRF double-submit token). */
function readCookie(name: string): string | undefined {
	const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));
	return match ? decodeURIComponent(match[1]) : undefined;
}

/** The CSRF header/cookie pair Spring Security's CookieCsrfTokenRepository expects. */
export function csrfHeaders(): Record<string, string> {
	// The double-submit cookie carries the __Host- prefix (host-only + Secure) so a sibling subdomain
	// cannot toss a forged token onto this host; the echoed header name stays X-XSRF-TOKEN. The cookie
	// name is configurable (environment.xsrfCookieName) because local http E2E drops the __Host- prefix.
	const token = readCookie(environment.xsrfCookieName);
	return token ? { "X-XSRF-TOKEN": token } : {};
}

/**
 * Mutate a state-changing request so it carries the CSRF double-submit header — and, when an operator
 * has explicitly enabled impersonation write-mode, the `X-Impersonation-Allow-Writes` header. Safe
 * methods (GET/HEAD/OPTIONS) get neither. This is the single CSRF guard applied to EVERY generated
 * mutation; `writesEnabled` is injected (not read from the store) so the guard is a pure, unit-testable
 * seam — see `main.tsx` for the wiring.
 */
export function applyStateChangingHeaders(request: Request, writesEnabled: boolean): Request {
	const method = (request.method ?? "GET").toUpperCase();
	if (method !== "GET" && method !== "HEAD" && method !== "OPTIONS") {
		for (const [key, value] of Object.entries(csrfHeaders())) {
			request.headers.set(key, value);
		}
		if (writesEnabled) {
			request.headers.set("X-Impersonation-Allow-Writes", "true");
		}
	}
	return request;
}

export const authClient = {
	/**
	 * Begin the OAuth login flow against the given provider (default: github).
	 *
	 * `returnTo` is the validated destination to land on after the dance completes; it is
	 * sanitised with the shared `safeReturnTo` open-redirect guard before being handed
	 * to the server (which echoes it back into the SPA callback). Callers must pass the
	 * intended destination — defaulting to the current page would send users back to /login.
	 */
	login(idpHint?: string, returnTo?: string): void {
		const provider = idpHint && idpHint.length > 0 ? idpHint : "github";
		const url = new URL(`${serverUrl()}/auth/login`);
		url.searchParams.set("provider", provider);
		url.searchParams.set("returnTo", safeReturnTo(returnTo));
		window.location.assign(url.toString());
	},

	/**
	 * Begin a link flow — attaches a new identity to the currently authenticated account.
	 *
	 * `returnTo` is sanitised with the shared `safeReturnTo` guard, same as `login`.
	 */
	linkAccount(providerAlias: string, returnTo?: string): void {
		const url = new URL(`${serverUrl()}/auth/login`);
		url.searchParams.set("provider", providerAlias);
		url.searchParams.set("mode", "link");
		url.searchParams.set("returnTo", safeReturnTo(returnTo));
		window.location.assign(url.toString());
	},

	/**
	 * Passwordless dev/test sign-in (server endpoint gated by {@code hephaestus.auth.dev-login-enabled},
	 * fail-closed in prod). Mints the same cookie session as the OAuth flow for a local account, then
	 * lands on the sanitised `returnTo`. Only reachable when the discovery list advertises the `dev`
	 * provider, so this is a no-op surface in production. Mirrors `logout`'s direct-fetch pattern (these
	 * auth kickoff/session calls are intentionally outside the generated client).
	 */
	async devLogin(username: string, admin: boolean, returnTo?: string): Promise<void> {
		const response = await fetch(`${serverUrl()}/auth/dev-login`, {
			method: "POST",
			credentials: "include",
			headers: { "Content-Type": "application/json", ...csrfHeaders() },
			body: JSON.stringify({ username, admin }),
		});
		if (!response.ok) {
			throw new Error(`Dev sign-in failed (${response.status})`);
		}
		window.location.assign(safeReturnTo(returnTo));
	},

	/** Revoke the session server-side, then return home. */
	async logout(): Promise<void> {
		try {
			await fetch(`${serverUrl()}/auth/logout`, {
				method: "POST",
				credentials: "include",
				headers: { ...csrfHeaders() },
			});
		} finally {
			window.location.assign("/");
		}
	},
};

/**
 * Build the `UserProfile` from the server's `CurrentUserView`.
 *
 * Accepts the raw generated view (every field optional) rather than the narrowed
 * `CurrentUser`, so the TanStack-Query-backed `useAuth()` can feed the query result
 * straight in without an unsafe cast. All reads are null-safe with `??` fallbacks.
 */
export function toUserProfile(user: CurrentUserView): UserProfile {
	const name = user.displayName ?? user.username ?? "";
	const [firstName, ...rest] = name.split(" ");
	return {
		id: String(user.id),
		username: user.username ?? "",
		firstName,
		lastName: rest.join(" "),
		email: user.primaryEmail ?? "",
		name,
		roles: user.roles ?? [],
		githubId: user.identityProvider === "GITHUB" ? (user.gitProviderId ?? undefined) : undefined,
		gitlabId: user.identityProvider === "GITLAB" ? (user.gitProviderId ?? undefined) : undefined,
		identityProvider: user.identityProvider ?? undefined,
		linkedProviders: (user.linkedProviders ?? []).map((p) => ({
			type: p.type ?? "",
			serverUrl: p.serverUrl ?? undefined,
		})),
	};
}
