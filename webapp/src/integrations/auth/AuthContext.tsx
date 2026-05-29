import type { ReactNode } from "react";
import { createContext, useContext, useEffect, useState } from "react";
import { authClient, type CurrentUser, toUserProfile, type UserProfile } from "./authClient";

export type { UserProfile } from "./authClient";

export interface AuthContextType {
	isAuthenticated: boolean;
	isLoading: boolean;
	username: string | undefined;
	userRoles: string[];
	/** True when the current account is an application super-admin (APP_ADMIN or `admin` role). */
	isAppAdmin: boolean;
	userProfile: UserProfile | undefined;
	login: (idpHint?: string, returnTo?: string) => Promise<void>;
	linkAccount: (providerAlias: string) => Promise<void>;
	logout: () => Promise<void>;
	hasRole: (role: string) => boolean;
	isCurrentUser: (login?: string) => boolean;
	getUserId: () => string | undefined;
	getGitProviderId: () => string | undefined;
	getUserProfilePictureUrl: () => string;
	getUserProfileUrl: () => string;
	/** Whether the user has a linked GitLab identity (logged in via GitLab or account linked) */
	hasGitLabIdentity: boolean;
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
 * Cookie-session auth provider (ADR 0017). On mount it reads GET /user (the session cookie is
 * sent automatically); a 401 means unauthenticated. The useAuth() API is unchanged from the
 * former keycloak-js implementation so existing consumers keep working.
 */
export function AuthProvider({ children }: AuthProviderProps) {
	const [isLoading, setIsLoading] = useState(true);
	const [user, setUser] = useState<CurrentUser | null>(null);

	useEffect(() => {
		let cancelled = false;
		authClient
			.fetchCurrentUser()
			.then((current) => {
				if (!cancelled) {
					setUser(current);
				}
			})
			.finally(() => {
				if (!cancelled) {
					setIsLoading(false);
				}
			});
		return () => {
			cancelled = true;
		};
	}, []);

	// No manual memoization: the project runs React Compiler, which memoizes these derived
	// values and stable callbacks automatically (see webapp/AGENTS.md).
	const userProfile = user ? toUserProfile(user) : undefined;

	const isAppAdmin = user?.appRole === "APP_ADMIN" || (user?.roles ?? []).includes("admin");

	const login = async (idpHint?: string, returnTo?: string) => {
		authClient.login(idpHint, returnTo);
	};

	const linkAccount = async (providerAlias: string) => {
		authClient.linkAccount(providerAlias);
	};

	const logout = async () => {
		await authClient.logout();
	};

	const hasRole = (role: string) => (user?.roles ?? []).includes(role);

	const isCurrentUser = (login?: string) =>
		!!login && !!user?.username && user.username.toLowerCase() === login.toLowerCase();

	const getUserId = () => (user ? String(user.id) : undefined);

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

	const getUserProfileUrl = () => user?.profileUrl ?? "";

	const value: AuthContextType = {
		isAuthenticated: user !== null,
		isLoading,
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
		getUserProfileUrl,
		hasGitLabIdentity: user?.hasGitLabIdentity ?? false,
		isImpersonating: user?.impersonating ?? false,
		impersonatedDisplayName: user?.displayName ?? undefined,
	};

	return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
