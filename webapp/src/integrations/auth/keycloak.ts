import Keycloak from "keycloak-js";
import environment from "@/environment";

export interface UserProfile {
	id: string;
	username: string;
	firstName: string;
	lastName: string;
	email: string;
	emailVerified: boolean;
	name: string;
	preferred_username: string;
	userProfileMetadata?: {
		attributes: Array<{
			name: string;
			displayName: string;
			required: boolean;
			readOnly: boolean;
			validators: Record<string, unknown>;
			multivalued: boolean;
		}>;
		groups: Array<{
			name: string;
			displayHeader: string;
			displayDescription: string;
		}>;
	};
	realmAccess?: { roles: string[] };
	resourceAccess?: Record<string, { roles: string[] }>;
	roles: string[];
	sub: string;
	token: string;
	githubId?: string; // Optional GitHub ID from token
	gitlabId?: string; // Optional GitLab ID from token
	identityProvider?: string; // IdP alias used for this session (e.g. "github", "gitlab-lrz")
}

class KeycloakService {
	private keycloak: Keycloak | null = null;
	private initialized = false;
	private initializationPromise: Promise<boolean> | null = null;
	private profile: UserProfile | undefined;

	/**
	 * Initialize the Keycloak instance
	 */
	public async init(): Promise<boolean> {
		// If already initialized, return the current authentication state
		if (this.initialized) {
			return this.keycloak?.authenticated || false;
		}

		// If initialization is in progress, wait for it to complete
		if (this.initializationPromise) {
			return this.initializationPromise;
		}

		// Start initialization
		this.initializationPromise = this.performInit();
		return this.initializationPromise;
	}

	private async performInit(): Promise<boolean> {
		try {
			// Double-check if already initialized (for race conditions)
			if (this.initialized) {
				return this.keycloak?.authenticated || false;
			}

			this.keycloak = new Keycloak({
				url: environment.keycloak.url,
				realm: environment.keycloak.realm,
				clientId: environment.keycloak.clientId,
			});

			const authenticated = await this.keycloak.init({
				onLoad: "check-sso",
				silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
				checkLoginIframe: false,
				silentCheckSsoFallback: false, // Prevent fallback redirects in strict mode
			});

			this.initialized = true;
			if (process.env.NODE_ENV !== "production") {
				console.debug("Keycloak initialized, authenticated:", authenticated);
			}

			if (authenticated) {
				// Load user profile
				try {
					this.profile = (await this.keycloak.loadUserProfile()) as UserProfile;
					this.profile.token = this.keycloak.token || "";
					this.profile.roles = this.keycloak.realmAccess?.roles || [];
				} catch (error) {
					console.error("Failed to load user profile:", error);
				}
			}

			return authenticated;
		} catch (error) {
			console.error("Failed to initialize Keycloak:", error);
			// Reset initialization state on error
			this.initialized = false;
			this.initializationPromise = null;
			return false;
		}
	}

	/**
	 * Check if the user is authenticated
	 */
	public isAuthenticated(): boolean {
		return this.keycloak?.authenticated || false;
	}

	/**
	 * Check if the token is expired
	 */
	public isTokenExpired(): boolean {
		if (!this.keycloak || !this.keycloak.tokenParsed) {
			return true;
		}

		if (!this.keycloak.tokenParsed.exp) {
			return true;
		}

		return this.keycloak.tokenParsed.exp <= Math.floor(Date.now() / 1000);
	}

	/**
	 * Update token if it's about to expire (used by interceptors)
	 */
	public async updateToken(minValidity = 60): Promise<boolean> {
		if (!this.keycloak) {
			return false;
		}
		// Only refresh if token is about to expire
		if (!this.keycloak.isTokenExpired(minValidity)) {
			return false;
		}

		try {
			const refreshed = await this.keycloak.updateToken(minValidity);
			if (refreshed && this.profile) {
				this.profile.token = this.keycloak.token || "";
			}
			return refreshed;
		} catch (error) {
			console.error("Failed to refresh token:", error);
			return false;
		}
	}

	/**
	 * Get the current auth token
	 */
	public getToken(): string | undefined {
		return this.keycloak?.token || undefined;
	}

	/**
	 * Get the current user's username
	 */
	public getUsername(): string {
		return this.keycloak?.tokenParsed?.preferred_username || "";
	}

	/**
	 * Get the current user's ID
	 */
	public getUserId(): string | undefined {
		return this.keycloak?.tokenParsed?.sub;
	}

	/**
	 * Get the identity provider alias used for the current session (e.g. "github", "gitlab-lrz").
	 */
	public getIdentityProvider(): string | undefined {
		return this.keycloak?.tokenParsed?.identity_provider;
	}

	/**
	 * Get the user's git provider ID if available (e.g. GitHub user ID).
	 * Returns whichever provider ID is available (GitHub or GitLab).
	 */
	public getGitProviderId(): string | undefined {
		return this.keycloak?.tokenParsed?.github_id ?? this.keycloak?.tokenParsed?.gitlab_id;
	}

	/**
	 * Get user roles from the token
	 */
	public getUserRoles(): string[] {
		return this.keycloak?.realmAccess?.roles || [];
	}

	/**
	 * Get user profile
	 */
	public getUserProfile(): UserProfile | undefined {
		return this.profile;
	}

	/**
	 * Get the user's profile picture URL from their identity provider.
	 * Supports GitHub (via avatar API) and falls back to empty for other providers.
	 */
	public getUserProfilePictureUrl(): string {
		const githubId = this.keycloak?.tokenParsed?.github_id;
		if (githubId) {
			return `https://avatars.githubusercontent.com/u/${githubId}`;
		}
		return "";
	}

	/**
	 * Get the user's profile URL on their identity provider.
	 * Supports GitHub and GitLab based on the identity provider used for login.
	 */
	public getUserProfileUrl(): string {
		const username = this.getUsername();
		if (!username) return "";
		const idp = this.getIdentityProvider();
		if (idp?.startsWith("gitlab")) {
			// Derive GitLab instance URL from the IdP alias convention (gitlab-lrz → gitlab.lrz.de)
			return `https://gitlab.lrz.de/${username}`;
		}
		return `https://github.com/${username}`;
	}

	/**
	 * Redirect to the Keycloak login page.
	 * Users can choose between configured identity providers (GitHub, GitLab, etc.).
	 * Pass an idpHint to skip the provider selection and go directly to a specific provider.
	 */
	public login(idpHint?: string): Promise<void> {
		return this.keycloak?.login(idpHint ? { idpHint } : undefined) || Promise.resolve();
	}

	/**
	 * Initiate account linking for the given identity provider.
	 * Uses Keycloak's Application Initiated Action (kc_action=idp_link) to redirect
	 * the user to the external provider's OAuth flow, then back to the given URL.
	 *
	 * Note: In Keycloak &lt;26.3, the redirect does not include kc_action_status,
	 * so the frontend cannot detect success/cancel. The linked accounts query
	 * refetches on return to show the updated state.
	 */
	public linkAccount(providerAlias: string, redirectUri?: string): Promise<void> {
		return (
			this.keycloak?.login({
				action: `idp_link:${providerAlias}`,
				redirectUri: redirectUri ?? window.location.href,
			}) || Promise.resolve()
		);
	}

	/**
	 * Logout the current user
	 */
	public logout(): Promise<void> {
		// Reset all state
		this.initialized = false;
		this.initializationPromise = null;
		this.profile = undefined;

		return (
			this.keycloak?.logout({
				redirectUri: window.location.origin,
			}) || Promise.resolve()
		);
	}

	/**
	 * Check if the user has the specified role
	 */
	public hasRole(role: string): boolean {
		return this.keycloak?.hasRealmRole(role) || false;
	}

	/**
	 * Check if the current user matches the given login
	 */
	public isCurrentUser(login?: string): boolean {
		if (!login) return false;
		const username = this.keycloak?.tokenParsed?.preferred_username;
		return username?.toLowerCase() === login.toLowerCase();
	}
}

// Export a singleton instance
const keycloakService = new KeycloakService();
export default keycloakService;
