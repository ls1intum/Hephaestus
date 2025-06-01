import {
	createContext,
	useCallback,
	useContext,
	useEffect,
	useState,
} from "react";
import type { ReactNode } from "react";
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
	logout: () => Promise<void>;
	hasRole: (role: string) => boolean;
	isCurrentUser: (login?: string) => boolean;
	getUserId: () => string | undefined;
	getUserGithubId: () => string | undefined;
	getUserGithubProfilePictureUrl: () => string;
	getUserGithubProfileUrl: () => string;
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
	const [userProfile, setUserProfile] = useState<UserProfile | undefined>(
		undefined,
	);

	// Clean the URL from authentication parameters
	const cleanUrlFromAuthParams = useCallback(() => {
		if (
			window.location.hash &&
			(window.location.hash.includes("state=") ||
				window.location.hash.includes("session_state=") ||
				window.location.hash.includes("code="))
		) {
			const baseUrl = window.location.pathname;
			console.log("Cleaning URL from auth params, redirecting to:", baseUrl);

			// Use history API to replace the current URL without auth parameters
			if (window.history?.replaceState) {
				window.history.replaceState(null, "", baseUrl);
				return true;
			}
		}
		return false;
	}, []);

	// Initialize Keycloak once
	useEffect(() => {
		// Prevent multiple initializations in strict mode
		if (globalState.initialized) {
			console.log("AuthProvider: Already initialized globally");
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
			console.log("AuthProvider: Waiting for existing initialization");
			globalState.initPromise
				.then((authenticated) => {
					console.log(
						"AuthProvider: Existing initialization completed:",
						authenticated,
					);
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

				console.log("AuthProvider: Initializing Keycloak");
				const authenticated = await keycloakService.init();
				console.log(
					"AuthProvider: Keycloak initialized, authenticated:",
					authenticated,
				);

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
				console.error(
					"AuthProvider: Failed to initialize authentication",
					error,
				);
				globalState.initPromise = null;
				throw error;
			} finally {
				setIsLoading(false);
			}
		};

		// Start initialization and store the promise
		globalState.initPromise = initKeycloak();
	}, [cleanUrlFromAuthParams]);

	const login = async () => {
		await keycloakService.login();
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

	const getUserGithubId = () => {
		return keycloakService.getUserGithubId();
	};

	const getUserGithubProfilePictureUrl = () => {
		return keycloakService.getUserGithubProfilePictureUrl();
	};

	const getUserGithubProfileUrl = () => {
		return keycloakService.getUserGithubProfileUrl();
	};

	const value = {
		isAuthenticated,
		isLoading,
		username,
		userRoles,
		userProfile,
		login,
		logout,
		hasRole,
		isCurrentUser,
		getUserId,
		getUserGithubId,
		getUserGithubProfilePictureUrl,
		getUserGithubProfileUrl,
	};

	return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
