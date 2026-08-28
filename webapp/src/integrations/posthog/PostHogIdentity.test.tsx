import { render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { AuthContextType } from "@/integrations/auth/AuthContext";

/** Only the calls this component makes; `usePostHogClient` is mocked, so nothing wider is needed. */
const client = {
	opt_in_capturing: vi.fn(),
	opt_out_capturing: vi.fn(),
	reset: vi.fn(),
	identify: vi.fn(),
	stopSessionRecording: vi.fn(),
	getActiveMatchingSurveys: vi.fn(),
};

const posthogConfig = { isPosthogEnabled: false };
const userSettingsQuery = {
	data: { participateInResearch: true },
	isLoading: false,
	isError: false,
};
const mockUseAuth = vi.fn<() => AuthContextType>();

// Only `useQuery` is stubbed: the generated `getUserSettingsOptions` still builds its real options
// off `queryOptions`, so a rename there fails this file rather than passing against a fake.
vi.mock("@tanstack/react-query", async (importOriginal) => ({
	...(await importOriginal<typeof import("@tanstack/react-query")>()),
	useQuery: () => userSettingsQuery,
}));

vi.mock("@/integrations/auth", () => ({
	useAuth: () => mockUseAuth(),
}));

vi.mock("./config", () => ({
	get isPosthogEnabled() {
		return posthogConfig.isPosthogEnabled;
	},
}));

vi.mock("./use-posthog-client", () => ({
	usePostHogClient: () => client,
}));

import { PostHogIdentity } from "./PostHogIdentity";

const signedIn: AuthContextType = {
	isAuthenticated: true,
	isLoading: false,
	isError: false,
	username: "octocat",
	userRoles: ["ROLE_USER"],
	isAppAdmin: false,
	userProfile: {
		id: "user-1",
		username: "octocat",
		email: "octocat@example.com",
		firstName: "Octo",
		lastName: "Cat",
		name: "Octo Cat",
		roles: ["ROLE_USER"],
		linkedProviders: [],
	},
	login: () => {},
	linkAccount: () => {},
	logout: () => Promise.resolve(),
	hasRole: () => false,
	isCurrentUser: () => false,
	getUserId: () => "user-1",
	getGitProviderId: () => undefined,
	getUserProfilePictureUrl: () => "",
	hasGitLabIdentity: false,
	linkedProviders: [],
	isImpersonating: false,
	impersonatedDisplayName: undefined,
};

describe("PostHogIdentity", () => {
	beforeEach(() => {
		mockUseAuth.mockReturnValue(signedIn);
		userSettingsQuery.data = { participateInResearch: true };
		userSettingsQuery.isLoading = false;
		userSettingsQuery.isError = false;
	});

	afterEach(() => {
		vi.clearAllMocks();
	});

	// The gate: with PostHog switched off the provider never mounts, so the client this reaches is
	// the un-`init`ed module singleton. Touching it at all would queue capture against no project.
	it("leaves the client alone when PostHog is disabled, even for a consenting signed-in user", () => {
		posthogConfig.isPosthogEnabled = false;

		render(<PostHogIdentity />);

		for (const call of Object.values(client)) {
			expect(call).not.toHaveBeenCalled();
		}
	});

	it("identifies a consenting signed-in user once PostHog is enabled", () => {
		posthogConfig.isPosthogEnabled = true;

		render(<PostHogIdentity />);

		expect(client.opt_in_capturing).toHaveBeenCalled();
		expect(client.identify).toHaveBeenCalledWith(
			"user-1",
			expect.objectContaining({ email: "octocat@example.com", participate_in_research: true }),
		);
	});

	it("opts out and clears state for a signed-in user who has not consented", () => {
		posthogConfig.isPosthogEnabled = true;
		userSettingsQuery.data = { participateInResearch: false };

		render(<PostHogIdentity />);

		expect(client.opt_out_capturing).toHaveBeenCalled();
		expect(client.reset).toHaveBeenCalled();
		expect(client.stopSessionRecording).toHaveBeenCalled();
		expect(client.identify).not.toHaveBeenCalled();
	});

	it("stays opted out while consent is still loading", () => {
		posthogConfig.isPosthogEnabled = true;
		userSettingsQuery.isLoading = true;

		render(<PostHogIdentity />);

		expect(client.opt_out_capturing).toHaveBeenCalled();
		expect(client.opt_in_capturing).not.toHaveBeenCalled();
	});
});
