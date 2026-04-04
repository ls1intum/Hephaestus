import type { ReactNode } from "react";
import { createContext, useContext, useEffect, useState } from "react";
import keycloakService, { type UserProfile } from "./keycloak";

// Global state to prevent duplicate initialization across strict mode renders
const globalState = {
	initialized: false,
	initPromise: null as Promise<boolean> | null,
};

export interface AuthContextType {
	isAuthenticated: boolean;
	isLoading: boolean;
	username: string | undefined;
	userRoles: string[];
	userProfile: UserProfile | undefined;
	login: () => Promise<void>;
	linkAccount: (providerAlias: string) => Promise<void>;
	logout: () => Promise<void>;
	hasRole: (role: string) => boolean;
	isCurrentUser: (login?: string) => boolean;
	getUserId: () => string | undefined;
	getGitProviderId: () => string | undefined;
	getUserProfilePictureUrl: () => string;
	getUserProfileUrl: () => string;
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

export function AuthProvider({ children }: AuthProviderProps) {
	const [isAuthenticated, setIsAuthenticated] = useState(false);
	const [isLoading, setIsLoading] = useState(true);
	const [username, setUsername] = useState<string | undefined>(undefined);
	const [userRoles, setUserRoles] = useState<string[]>([]);
	const [userProfile, setUserProfile] = useState<UserProfile | undefined>(undefined);

	// Initialize Keycloak once
	useEffect(() => {
		const cleanUrlFromAuthParams = () => {
			if (
				window.location.hash &&
				(window.location.hash.includes("state=") ||
					window.location.hash.includes("session_state=") ||
					window.location.hash.includes("code="))
			) {
				const baseUrl = window.location.pathname + window.location.search;
				if (process.env.NODE_ENV !== "production") {
					console.debug("AuthProvider: Cleaning URL from auth params, redirecting to:", baseUrl);
				}

				// Use history API to replace the current URL without auth parameters
				if (window.history?.replaceState) {
					window.history.replaceState(null, "", baseUrl);
					return true;
				}
			}
			return false;
		};

		// Prevent multiple initializations in strict mode
		if (globalState.initialized) {
			if (process.env.NODE_ENV !== "production") {
				console.debug("AuthProvider: Already initialized globally");
			}
			setIsLoading(false);

			// Update local state with current authentication status
			const authenticated = keycloakService.isAuthenticated();
			if (authenticated) {
				const userName = keycloakService.getUsername();
				const roles = keycloakService.getUserRoles();
				const profile = keycloakService.getUserProfile();
				setUsername(userName);
				setUserRoles(roles);
				setUserProfile(profile);
			}
			setIsAuthenticated(authenticated);
			return;
		}

		// If initialization is already in progress, wait for it
		if (globalState.initPromise) {
			if (process.env.NODE_ENV !== "production") {
				console.debug("AuthProvider: Waiting for existing initialization");
			}
			globalState.initPromise
				.then((authenticated) => {
					if (process.env.NODE_ENV !== "production") {
						console.debug("AuthProvider: Existing initialization completed:", authenticated);
					}
					if (authenticated) {
						const userName = keycloakService.getUsername();
						const roles = keycloakService.getUserRoles();
						const profile = keycloakService.getUserProfile();
						setUsername(userName);
						setUserRoles(roles);
						setUserProfile(profile);
					}
					setIsAuthenticated(authenticated);
					setIsLoading(false);
				})
				.catch((error) => {
					console.error("AuthProvider: Initialization failed:", error);
					setIsLoading(false);
				});
			return;
		}

		const initKeycloak = async () => {
			try {
				// Clean URL from auth parameters first
				cleanUrlFromAuthParams();

				if (process.env.NODE_ENV !== "production") {
					console.debug("AuthProvider: Initializing Keycloak");
				}
				const authenticated = await keycloakService.init();
				if (process.env.NODE_ENV !== "production") {
					console.debug("AuthProvider: Keycloak initialized, authenticated:", authenticated);
				}

				if (authenticated) {
					const userName = keycloakService.getUsername();
					const roles = keycloakService.getUserRoles();
					const profile = keycloakService.getUserProfile();

					setUsername(userName);
					setUserRoles(roles);
					setUserProfile(profile);
				}

				setIsAuthenticated(authenticated);
				globalState.initialized = true;
				globalState.initPromise = null;
				return authenticated;
			} catch (error) {
				console.error("AuthProvider: Failed to initialize authentication", error);
				globalState.initPromise = null;
				throw error;
			} finally {
				setIsLoading(false);
			}
		};

		// Start initialization and store the promise
		globalState.initPromise = initKeycloak();
	}, []);

	const login = async () => {
		await keycloakService.login();
	};

	const linkAccount = async (providerAlias: string) => {
		await keycloakService.linkAccount(providerAlias);
	};

	const logout = async () => {
		// Reset global initialization state
		globalState.initialized = false;
		globalState.initPromise = null;

		await keycloakService.logout();
	};

	const hasRole = (role: string) => {
		return keycloakService.hasRole(role);
	};

	const isCurrentUser = (login?: string) => {
		return keycloakService.isCurrentUser(login);
	};

	const getUserId = () => {
		return keycloakService.getUserId();
	};

	const getGitProviderId = () => {
		return keycloakService.getGitProviderId();
	};

	const getUserProfilePictureUrl = () => {
		return keycloakService.getUserProfilePictureUrl();
	};

	const getUserProfileUrl = () => {
		return keycloakService.getUserProfileUrl();
	};

	const value = {
		isAuthenticated,
		isLoading,
		username,
		userRoles,
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
	};

	return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
