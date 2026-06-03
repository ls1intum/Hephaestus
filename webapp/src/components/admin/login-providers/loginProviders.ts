import type { ConnectionSummary } from "@/api/types.gen";

export { problemDetailOf } from "@/lib/problem-detail";

/** Connection kinds in the IDENTITY family — workspace-scoped OIDC *login* providers. */
export type LoginProviderKind = "OIDC_LOGIN_GITHUB" | "OIDC_LOGIN_GITLAB";

export const IDENTITY_LOGIN_KINDS: readonly LoginProviderKind[] = [
	"OIDC_LOGIN_GITHUB",
	"OIDC_LOGIN_GITLAB",
];

/** Lifecycle state of a Connection, as surfaced by the registry. */
export type ConnectionState = NonNullable<ConnectionSummary["state"]>;

export const PROVIDER_LABELS: Record<LoginProviderKind, string> = {
	OIDC_LOGIN_GITHUB: "GitHub Enterprise",
	OIDC_LOGIN_GITLAB: "Self-hosted GitLab",
};

/**
 * Spring's OAuth callback registration id prefix per kind. The server builds the redirect
 * URI as `<baseUrl>/login/oauth2/code/{prefix}-ws-{connectionId}` and serves it under
 * the `/api` context path (see application.yml). The admin must register THIS URL in
 * their IdP's OAuth app.
 */
const CALLBACK_PREFIX: Record<LoginProviderKind, string> = {
	OIDC_LOGIN_GITHUB: "gh",
	OIDC_LOGIN_GITLAB: "gl",
};

/**
 * Build the OAuth callback URL the admin must register in their provider's app.
 * The connectionId only exists after the connection is created, hence the chicken-and-egg
 * flow surfaced in the UI (create first, then copy this URL into the IdP app).
 */
export function callbackUrlFor(
	apiOrigin: string,
	kind: LoginProviderKind,
	connectionId: number,
): string {
	const origin = apiOrigin.replace(/\/+$/, "");
	return `${origin}/api/login/oauth2/code/${CALLBACK_PREFIX[kind]}-ws-${connectionId}`;
}

/** Badge variant for a connection state. */
export function stateBadgeVariant(
	state: ConnectionState | undefined,
): "default" | "secondary" | "destructive" | "outline" {
	switch (state) {
		case "ACTIVE":
			return "default";
		case "SUSPENDED":
			return "secondary";
		case "UNINSTALLED":
			return "destructive";
		default:
			return "outline";
	}
}

export function stateLabel(state: ConnectionState | undefined): string {
	switch (state) {
		case "ACTIVE":
			return "Active";
		case "SUSPENDED":
			return "Suspended";
		case "UNINSTALLED":
			return "Disconnected";
		case "PENDING":
			return "Pending";
		default:
			return state ?? "Unknown";
	}
}
