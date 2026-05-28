import type { ReactNode } from "react";
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { authClient, type CurrentUser, toUserProfile, type UserProfile } from "./authClient";

export type { UserProfile } from "./authClient";

export interface AuthContextType {
	isAuthenticated: boolean;
	isLoading: boolean;
	username: string | undefined;
	userRoles: string[];
	userProfile: UserProfile | undefined;
	login: (idpHint?: string) => Promise<void>;
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

	const userProfile = useMemo(() => (user ? toUserProfile(user) : undefined), [user]);

	const login = useCallback(async (idpHint?: string) => {
		authClient.login(idpHint);
	}, []);

	const linkAccount = useCallback(async (providerAlias: string) => {
		authClient.linkAccount(providerAlias);
	}, []);

	const logout = useCallback(async () => {
		await authClient.logout();
	}, []);

	const hasRole = useCallback((role: string) => (user?.roles ?? []).includes(role), [user]);

	const isCurrentUser = useCallback(
		(login?: string) => !!login && !!user?.username && user.username.toLowerCase() === login.toLowerCase(),
		[user],
	);

	const getUserId = useCallback(() => (user ? String(user.id) : undefined), [user]);

	const getGitProviderId = useCallback(() => user?.gitProviderId ?? undefined, [user]);

	const getUserProfilePictureUrl = useCallback(() => {
		if (user?.avatarUrl) {
			return user.avatarUrl;
		}
		if (user?.identityProvider === "GITHUB" && user.gitProviderId) {
			return `https://avatars.githubusercontent.com/u/${user.gitProviderId}`;
		}
		return "";
	}, [user]);

	const getUserProfileUrl = useCallback(() => user?.profileUrl ?? "", [user]);

	const value = useMemo<AuthContextType>(
		() => ({
			isAuthenticated: user !== null,
			isLoading,
			username: user?.username ?? undefined,
			userRoles: user?.roles ?? [],
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
		}),
		[
			user,
			isLoading,
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
		],
	);

	return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
