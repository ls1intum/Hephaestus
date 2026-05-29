import type { CurrentUserView } from "@/api/types.gen";
import environment from "@/environment";
import { safeReturnTo } from "@/integrations/auth/guard";

/**
 * Cookie-session auth client (replaces the former keycloak-js singleton; ADR 0017).
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

/** Profile shape preserved for the `useAuth().userProfile` consumers. */
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
}

const serverUrl = () => environment.serverUrl.replace(/\/$/, "");

/** Read a cookie value by name (used for the CSRF double-submit token). */
function readCookie(name: string): string | undefined {
	const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));
	return match ? decodeURIComponent(match[1]) : undefined;
}

/** The CSRF header/cookie pair Spring Security's CookieCsrfTokenRepository expects. */
export function csrfHeaders(): Record<string, string> {
	const token = readCookie("XSRF-TOKEN");
	return token ? { "X-XSRF-TOKEN": token } : {};
}

export const authClient = {
	/** Fetch the current user, or null when unauthenticated (401). */
	async fetchCurrentUser(): Promise<CurrentUser | null> {
		try {
			const res = await fetch(`${serverUrl()}/user`, {
				method: "GET",
				credentials: "include",
				headers: { Accept: "application/json" },
			});
			if (res.status === 401 || res.status === 403) {
				return null;
			}
			if (!res.ok) {
				return null;
			}
			return (await res.json()) as CurrentUser;
		} catch {
			return null;
		}
	},

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

/** Build the preserved `UserProfile` from a `CurrentUser` (server `CurrentUserView`). */
export function toUserProfile(user: CurrentUser): UserProfile {
	const name = user.displayName ?? user.username ?? "";
	const [firstName, ...rest] = name.split(" ");
	return {
		id: String(user.id),
		username: user.username ?? "",
		firstName: firstName ?? name,
		lastName: rest.join(" "),
		email: user.primaryEmail ?? "",
		name,
		roles: user.roles ?? [],
		githubId: user.identityProvider === "GITHUB" ? (user.gitProviderId ?? undefined) : undefined,
		gitlabId: user.identityProvider === "GITLAB" ? (user.gitProviderId ?? undefined) : undefined,
		identityProvider: user.identityProvider ?? undefined,
	};
}
