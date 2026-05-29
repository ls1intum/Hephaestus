// Realistic fixtures for the native-auth endpoints (ADR 0017), shared by the MSW
// handlers and available for per-story overrides. Shapes mirror the generated
// `src/api/types.gen.ts` views so stories render against the real component code
// paths. Dates are ISO strings on the wire; the hey-api transformers revive them
// into `Date` objects, matching how the server serializes them.

import type {
	AdminAccountView,
	ConnectionSummary,
	CurrentUserView,
	ExportStatus,
	IdentityProviderView,
	IdentityView,
	SessionView,
} from "@/api/types.gen";

export const currentUser: CurrentUserView = {
	id: 42,
	username: "octocat",
	displayName: "The Octocat",
	primaryEmail: "octocat@example.com",
	avatarUrl: "https://avatars.githubusercontent.com/u/583231?v=4",
	profileUrl: "https://github.com/octocat",
	appRole: "APP_ADMIN",
	roles: ["ROLE_USER", "ROLE_ADMIN"],
	status: "ACTIVE",
	identityProvider: "github",
	gitProviderId: "583231",
	hasGitLabIdentity: true,
	impersonating: false,
};

export const identityProviders: IdentityProviderView[] = [
	{ registrationId: "github", displayName: "GitHub", providerType: "GITHUB" },
	{ registrationId: "gitlab-lrz", displayName: "GitLab LRZ", providerType: "GITLAB" },
];

export const linkedIdentities: IdentityView[] = [
	{
		id: 1,
		providerType: "GITHUB",
		username: "octocat",
		displayName: "The Octocat",
		subject: "583231",
		avatarUrl: "https://avatars.githubusercontent.com/u/583231?v=4",
		lastLoginAt: "2026-05-20T10:00:00Z" as unknown as Date,
	},
	{
		id: 2,
		providerType: "GITLAB",
		username: "octocat-lrz",
		displayName: "Octo (LRZ)",
		subject: "991122",
		lastLoginAt: "2026-04-02T08:30:00Z" as unknown as Date,
	},
];

export const sessions: SessionView[] = [
	{
		jti: "sess-current-001",
		current: true,
		userAgent: "Chrome 124 on macOS",
		ip: "192.0.2.10",
		issuedAt: "2026-05-29T09:00:00Z" as unknown as Date,
		expiresAt: "2026-06-29T09:00:00Z" as unknown as Date,
	},
	{
		jti: "sess-other-002",
		current: false,
		userAgent: "Firefox 126 on Ubuntu",
		ip: "198.51.100.23",
		issuedAt: "2026-05-25T14:12:00Z" as unknown as Date,
		expiresAt: "2026-06-25T14:12:00Z" as unknown as Date,
	},
	{
		jti: "sess-other-003",
		current: false,
		userAgent: "Mobile Safari on iOS 18",
		ip: "203.0.113.77",
		issuedAt: "2026-05-21T07:45:00Z" as unknown as Date,
		expiresAt: "2026-06-21T07:45:00Z" as unknown as Date,
	},
];

export const adminUsers: AdminAccountView[] = [
	{
		id: 42,
		displayName: "The Octocat",
		primaryEmail: "octocat@example.com",
		appRole: "APP_ADMIN",
		status: "ACTIVE",
	},
	{
		id: 7,
		displayName: "Ada Lovelace",
		primaryEmail: "ada@example.com",
		appRole: "APP_USER",
		status: "ACTIVE",
	},
	{
		id: 99,
		displayName: "Suspended Sam",
		primaryEmail: "sam@example.com",
		appRole: "APP_USER",
		status: "SUSPENDED",
	},
];

// IDENTITY-family connections backing the workspace login-providers screen. These
// share the generic `/connections` endpoint with SCM/messaging connections; the UI
// filters client-side to the OIDC_LOGIN_* kinds.
export const identityConnections: ConnectionSummary[] = [
	{
		id: 501,
		kind: "OIDC_LOGIN_GITHUB",
		family: "IDENTITY",
		displayName: "GitHub Enterprise (login)",
		instanceKey: "ghe-corp",
		state: "ACTIVE",
		capabilities: [],
		createdAt: "2026-03-01T12:00:00Z" as unknown as Date,
		updatedAt: "2026-05-01T12:00:00Z" as unknown as Date,
		lastActivityAt: "2026-05-28T18:30:00Z" as unknown as Date,
	},
	{
		id: 502,
		kind: "OIDC_LOGIN_GITLAB",
		family: "IDENTITY",
		displayName: "Self-hosted GitLab (login)",
		instanceKey: "gitlab-lrz",
		state: "SUSPENDED",
		stateReason: "Credentials rotated by admin",
		capabilities: [],
		createdAt: "2026-02-14T09:00:00Z" as unknown as Date,
		updatedAt: "2026-05-10T09:00:00Z" as unknown as Date,
	},
];

// Sequential export-status snapshots: PENDING on first poll, READY afterwards so a
// story / test exercising the DangerZone export flow sees the state transition.
export const exportPending: ExportStatus = {
	id: 9001,
	status: "PENDING",
	requestedAt: "2026-05-29T10:00:00Z" as unknown as Date,
};

export const exportReady: ExportStatus = {
	id: 9001,
	status: "READY",
	requestedAt: "2026-05-29T10:00:00Z" as unknown as Date,
	completedAt: "2026-05-29T10:00:05Z" as unknown as Date,
	expiresAt: "2026-06-05T10:00:05Z" as unknown as Date,
};
