import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, fn, screen } from "storybook/test";
import type { IdentityProviderView } from "@/api/types.gen";
import { AuthProvider } from "@/integrations/auth/AuthContext";
import { ConfirmAccessDialog } from "./ConfirmAccessDialog";

const PROVIDERS: IdentityProviderView[] = [
	{ registrationId: "github", displayName: "GitHub", providerType: "GITHUB" },
	{ registrationId: "gitlab-lrz", displayName: "GitLab LRZ", providerType: "GITLAB" },
];

/** The signed-in admin, whose provider decides which button the challenge may offer. */
function currentUser(identityProvider: string) {
	return HttpResponse.json({
		id: 1,
		login: "admin",
		name: "Ada Admin",
		identityProvider,
		roles: ["app_admin"],
		linkedProviders: [{ type: identityProvider }],
	});
}

/**
 * The step-up re-auth prompt shown when the server refuses a high-risk admin action with
 * `403 step_up_required`.
 */
const meta = {
	component: ConfirmAccessDialog,
	parameters: {
		layout: "centered",
		msw: {
			handlers: [
				http.get("*/identity-providers", () => HttpResponse.json(PROVIDERS)),
				http.get("*/user", () => currentUser("GITHUB")),
			],
		},
	},
	args: { open: true, onOpenChange: fn(), maxAgeSeconds: 300 },
	decorators: [
		(Story) => (
			<AuthProvider>
				<Story />
			</AuthProvider>
		),
	],
} satisfies Meta<typeof ConfirmAccessDialog>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Only the provider the admin signed in with is offered: any other would silently switch them to a
 * different account instead of confirming this one. This instance also has GitLab configured.
 */
export const OnlyTheCurrentProvider: Story = {
	play: async () => {
		await screen.findByRole("dialog");
		await expect(await screen.findByRole("button", { name: /continue with github/i })).toBeTruthy();
		await expect(screen.queryByRole("button", { name: /gitlab/i })).toBeNull();
		// The copy states the server's actual window rather than a hardcoded duration.
		await expect(screen.getByText(/last 5 minutes/i)).toBeTruthy();
	},
};

/** A GitLab-authenticated admin gets the GitLab button on the same instance. */
export const GitLabAdmin: Story = {
	parameters: {
		msw: {
			handlers: [
				http.get("*/identity-providers", () => HttpResponse.json(PROVIDERS)),
				http.get("*/user", () => currentUser("GITLAB")),
			],
		},
	},
	play: async () => {
		await expect(await screen.findByRole("button", { name: /continue with gitlab/i })).toBeTruthy();
		await expect(screen.queryByRole("button", { name: /github/i })).toBeNull();
	},
};

/**
 * Fallback: the admin's primary identity is a link-only provider (Slack), which can never satisfy the
 * challenge. Rather than render an unusable empty dialog, every sign-in provider is offered.
 */
export const LinkOnlyPrimaryIdentityFallsBackToAllProviders: Story = {
	parameters: {
		msw: {
			handlers: [
				http.get("*/identity-providers", () => HttpResponse.json(PROVIDERS)),
				http.get("*/user", () => currentUser("SLACK")),
			],
		},
	},
	play: async () => {
		await expect(await screen.findByRole("button", { name: /continue with github/i })).toBeTruthy();
		await expect(screen.getByRole("button", { name: /continue with gitlab/i })).toBeTruthy();
	},
};
