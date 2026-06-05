import { useQuery } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { createContext, useContext } from "react";
import { getCurrentUserOptions } from "@/api/@tanstack/react-query.gen";
import { authClient, toUserProfile, type UserProfile } from "./authClient";
import { isAppAdmin as computeIsAppAdmin } from "./guard";

export type { UserProfile } from "./authClient";

export interface AuthContextType {
	isAuthenticated: boolean;
	isLoading: boolean;
	/** True when the `/user` probe settled in an error state (e.g. 401/403) rather than returning a user. */
	isError: boolean;
	username: string | undefined;
	userRoles: string[];
	/** True when the current account is an application super-admin (APP_ADMIN or `admin` role). */
	isAppAdmin: boolean;
	userProfile: UserProfile | undefined;
	login: (idpHint?: string, returnTo?: string) => Promise<void>;
	linkAccount: (providerAlias: string, returnTo?: string) => Promise<void>;
	logout: () => Promise<void>;
	hasRole: (role: string) => boolean;
	isCurrentUser: (login?: string) => boolean;
	getUserId: () => string | undefined;
	getGitProviderId: () => string | undefined;
	getUserProfilePictureUrl: () => string;
	/** Whether the user has a linked GitLab identity (logged in via GitLab or account linked) */
	hasGitLabIdentity: boolean;
	/** SCM instances the account has an active identity on — for instance-scoped gating. */
	linkedProviders: Array<{ type: string; serverUrl?: string }>;
	/** True when the current session is impersonating another account. */
	isImpersonating: boolean;
	/** Display name of the impersonated account (the current user) while impersonating. */
	impersonatedDisplayName: string | undefined;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function useAuth() {
	const context = useContext(AuthContext);
	if (!context) {
		throw new Error("useAuth must be used within an AuthProvider");
	}
	return context;
}

interface AuthProviderProps {
	children: ReactNode;
}

/**
 * Cookie-session auth provider (ADR 0017).
 *
 * Backed by the SAME TanStack Query (`getCurrentUserOptions()`, key `getCurrentUser`) that the
 * route guards read via `resolveCurrentUser`, so there is ONE shared cache — no duplicate
 * `/user` fetch on load and no drift (e.g. an admin changing their own role invalidates this
 * query and the in-app surface updates with it). A 401/403 makes the query error with no data,
 * which we treat as unauthenticated. The `useAuth()` API is unchanged from the former
 * keycloak-js implementation so existing consumers keep working.
 */
export function AuthProvider({ children }: AuthProviderProps) {
	// Don't retry on auth failure (a 401 is a definitive "not signed in", not a transient error)
	// and keep the result fresh-enough to avoid a refetch storm; the guards seed the same cache.
	const userQuery = useQuery({
		...getCurrentUserOptions(),
		retry: false,
		staleTime: 30_000,
	});

	const user = userQuery.data ?? null;
	// `isPending` is true only while the very first fetch is in flight (no cached data yet),
	// matching the previous mount-time loading window.
	const isLoading = userQuery.isPending;
	// Distinguish "probe failed" (401/403/network) from "probe settled with no user". Callers that
	// must not optimistically route into a protected area on a failed probe (e.g. /auth/callback)
	// branch on this instead of relying on a downstream guard to bounce them back.
	const isError = userQuery.isError;

	const userProfile = user ? toUserProfile(user) : undefined;

	const isAppAdmin = computeIsAppAdmin(user);

	const login = async (idpHint?: string, returnTo?: string) => {
		authClient.login(idpHint, returnTo);
	};

	const linkAccount = async (providerAlias: string, returnTo?: string) => {
		// Thread `returnTo` so a link initiated from settings returns to settings (defaulting to
		// the current page), rather than dumping the user back on `/` after the OAuth dance.
		const destination =
			returnTo ?? (typeof window !== "undefined" ? window.location.pathname : undefined);
		authClient.linkAccount(providerAlias, destination);
	};

	const logout = async () => {
		await authClient.logout();
	};

	const hasRole = (role: string) => (user?.roles ?? []).includes(role);

	const isCurrentUser = (login?: string) =>
		!!login && !!user?.username && user.username.toLowerCase() === login.toLowerCase();

	// Return undefined (never the string "undefined" / a value that coerces to NaN) until the
	// user is loaded, so callers like `Number(getUserId())` get `NaN` only when there genuinely
	// is no id — and can guard on `undefined` instead.
	const getUserId = () => (user?.id != null ? String(user.id) : undefined);

	const getGitProviderId = () => user?.gitProviderId ?? undefined;

	const getUserProfilePictureUrl = () => {
		if (user?.avatarUrl) {
			return user.avatarUrl;
		}
		if (user?.identityProvider === "GITHUB" && user.gitProviderId) {
			return `https://avatars.githubusercontent.com/u/${user.gitProviderId}`;
		}
		return "";
	};

	const value: AuthContextType = {
		isAuthenticated: user !== null,
		isLoading,
		isError,
		username: user?.username ?? undefined,
		userRoles: user?.roles ?? [],
		isAppAdmin,
		userProfile,
		login,
		linkAccount,
		logout,
		hasRole,
		isCurrentUser,
		getUserId,
		getGitProviderId,
		getUserProfilePictureUrl,
		hasGitLabIdentity: user?.hasGitLabIdentity ?? false,
		linkedProviders: userProfile?.linkedProviders ?? [],
		isImpersonating: user?.impersonating ?? false,
		impersonatedDisplayName: user?.displayName ?? undefined,
	};

	return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
