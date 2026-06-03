import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import type { IdentityProviderView, IdentityView } from "@/api/types.gen";
import { LinkedAccountsSection } from "./LinkedAccountsSection";

const github: IdentityView = {
	id: 1,
	providerType: "GITHUB",
	subject: "12345",
	username: "octocat",
	displayName: "The Octocat",
	lastLoginAt: new Date("2026-05-20T10:00:00Z"),
};

const gitlab: IdentityView = {
	id: 2,
	providerType: "GITLAB",
	subject: "67890",
	username: "tux",
	displayName: "Tux",
	lastLoginAt: new Date("2026-04-02T09:30:00Z"),
};

const githubProvider: IdentityProviderView = {
	registrationId: "github",
	displayName: "GitHub",
	providerType: "GITHUB",
};
const gitlabProvider: IdentityProviderView = {
	registrationId: "gitlab-lrz",
	displayName: "GitLab",
	providerType: "GITLAB",
};

/**
 * Connected-accounts settings section (ADR 0017 native auth). Presentation-only: identities,
 * linkable providers, and the link/unlink callbacks are passed in. Shown here against static
 * fixtures so every state — including the last-identity lockout guard and an in-flight
 * disconnect — is reviewable in isolation.
 */
const meta = {
	component: LinkedAccountsSection,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	argTypes: {
		onLink: { description: "Start the sign-in/link flow for a provider" },
		onUnlink: { description: "Disconnect a linked identity by id" },
		unlinkingId: {
			control: "number",
			description: "Identity currently being disconnected (shows a spinner, blocks repeat clicks)",
		},
		isLoading: { control: "boolean" },
		isError: { control: "boolean" },
	},
	args: {
		onLink: fn(),
		onUnlink: fn(),
		identities: [github, gitlab],
		providers: [githubProvider, gitlabProvider],
	},
} satisfies Meta<typeof LinkedAccountsSection>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Two connected providers — either can be disconnected (a confirmation dialog gates it). */
export const Default: Story = {
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		const triggers = await canvas.findAllByRole("button", { name: /^disconnect /i });
		await expect(triggers).toHaveLength(2);
		await expect(triggers[0]).toBeEnabled();

		// Drive the confirm flow: open the dialog, then confirm. The dialog renders in a portal,
		// so query the whole document for its destructive "Disconnect" action.
		await userEvent.click(triggers[0]);
		const action = await screen.findByRole("button", { name: "Disconnect" });
		await userEvent.click(action);
		await expect(args.onUnlink).toHaveBeenCalledWith(1);
	},
};

/** Only one identity — disconnect is unavailable so the account can never lock itself out. */
export const SingleIdentity: Story = {
	args: { identities: [github], providers: [githubProvider] },
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		// The lockout reason is always-visible text (not a disabled button), so SR/keyboard users
		// can read why disconnect is unavailable.
		await expect(canvas.getByText(/only sign-in method/i)).toBeInTheDocument();
		await expect(args.onUnlink).not.toHaveBeenCalled();
	},
};

/** One identity plus another provider available to connect. */
export const WithLinkableProvider: Story = {
	args: { identities: [github] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: /connect gitlab/i })).toBeInTheDocument();
	},
};

/** A disconnect is in flight — that row shows a spinner and the button is disabled. */
export const Disconnecting: Story = {
	args: { unlinkingId: 1 },
};

/** No connected accounts yet — empty state, with providers offered to connect. */
export const Empty: Story = {
	args: { identities: [] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/no connected accounts yet/i)).toBeInTheDocument();
	},
};

/** Loading the connected accounts. */
export const Loading: Story = {
	args: { isLoading: true },
};

/** Failed to load — error copy. */
export const ErrorState: Story = {
	args: { isError: true, identities: [], providers: [] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/failed to load connected accounts/i)).toBeInTheDocument();
	},
};
