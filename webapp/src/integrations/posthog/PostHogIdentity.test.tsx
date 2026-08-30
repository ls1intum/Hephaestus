import { render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { AuthContextType } from "@/integrations/auth/AuthContext";

const client = {
	opt_in_capturing: vi.fn(),
	opt_out_capturing: vi.fn(),
	reset: vi.fn(),
	identify: vi.fn(),
	stopSessionRecording: vi.fn(),
	getActiveMatchingSurveys: vi.fn(),
};

const posthogConfig = { isPosthogEnabled: false };
const consentQuery = {
	data: { participateInResearch: true },
	isLoading: false,
	isError: false,
};
const mockUseAuth = vi.fn<() => AuthContextType>();

vi.mock("@tanstack/react-query", async (importOriginal) => ({
	...(await importOriginal<typeof import("@tanstack/react-query")>()),
	useQuery: () => consentQuery,
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
		consentQuery.data = { participateInResearch: true };
		consentQuery.isLoading = false;
		consentQuery.isError = false;
	});

	afterEach(() => {
		vi.clearAllMocks();
	});

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
		consentQuery.data = { participateInResearch: false };

		render(<PostHogIdentity />);

		expect(client.opt_out_capturing).toHaveBeenCalled();
		expect(client.reset).toHaveBeenCalled();
		expect(client.stopSessionRecording).toHaveBeenCalled();
		expect(client.identify).not.toHaveBeenCalled();
	});

	it("stays opted out while consent is still loading", () => {
		posthogConfig.isPosthogEnabled = true;
		consentQuery.isLoading = true;

		render(<PostHogIdentity />);

		expect(client.opt_out_capturing).toHaveBeenCalled();
		expect(client.opt_in_capturing).not.toHaveBeenCalled();
	});

	it("stays opted out when the consent status cannot be loaded", () => {
		posthogConfig.isPosthogEnabled = true;
		consentQuery.isError = true;

		render(<PostHogIdentity />);

		expect(client.opt_out_capturing).toHaveBeenCalled();
		expect(client.reset).toHaveBeenCalled();
		expect(client.identify).not.toHaveBeenCalled();
	});
});
