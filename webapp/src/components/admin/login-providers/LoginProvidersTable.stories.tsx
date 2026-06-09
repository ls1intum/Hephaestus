import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, within } from "storybook/test";
import type { LoginProviderView } from "@/api/types.gen";
import { LoginProvidersTable } from "./LoginProvidersTable";

const providers: LoginProviderView[] = [
	{
		registrationId: "github",
		type: "GITHUB",
		displayName: "GitHub",
		baseUrl: "https://github.com",
		scopes: "read:user user:email",
		enabled: true,
		seededFromEnv: true,
		redirectUri: "https://hephaestus.example.com/api/login/oauth2/code/github",
		createdAt: new Date("2026-05-01T00:00:00Z"),
		updatedAt: new Date("2026-05-01T00:00:00Z"),
	},
	{
		registrationId: "gitlab-acme",
		type: "GITLAB",
		displayName: "ACME GitLab",
		baseUrl: "https://gitlab.acme.test",
		scopes: "read_user",
		enabled: false,
		seededFromEnv: false,
		redirectUri: "https://hephaestus.example.com/api/login/oauth2/code/gitlab-acme",
		createdAt: new Date("2026-05-02T00:00:00Z"),
		updatedAt: new Date("2026-05-02T00:00:00Z"),
	},
];

const meta = {
	component: LoginProvidersTable,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		providers,
		isLoading: false,
		isError: false,
		mutatingId: null,
		onEdit: fn(),
		onToggleEnabled: fn(),
		onDelete: fn(),
	},
} satisfies Meta<typeof LoginProvidersTable>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Env-seeded providers are badged "seeded"; each row exposes its upstream redirect URI to copy. */
export const Default: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("GitHub")).toBeInTheDocument();
		await expect(canvas.getByText("ACME GitLab")).toBeInTheDocument();
		// The env-seeded row carries the "seeded" badge; the admin-created one does not.
		await expect(canvas.getByText("seeded")).toBeInTheDocument();
		await expect(
			canvas.getByRole("button", { name: /Copy redirect URI for GitHub/i }),
		).toBeInTheDocument();
	},
};

/** A row mid-mutation disables its own toggle/edit/delete so concurrent edits can't race. */
export const RowBusy: Story = {
	args: { mutatingId: "github" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: /Edit GitHub/i })).toBeDisabled();
		await expect(canvas.getByRole("button", { name: /Edit ACME GitLab/i })).toBeEnabled();
	},
};

/** Empty state nudges the admin to add the first provider. */
export const Empty: Story = {
	args: { providers: [] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/No login providers yet/i)).toBeInTheDocument();
	},
};

/** Error state when the list fails to load. */
export const ErrorState: Story = {
	args: { providers: [], isError: true },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/Could not load login providers/i)).toBeInTheDocument();
	},
};

/** Loading spinner before the list resolves. */
export const Loading: Story = {
	args: { providers: [], isLoading: true },
};
