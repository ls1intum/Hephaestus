import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen } from "storybook/test";
import type { LoginProviderView } from "@/api/types.gen";
import { LoginProviderFormDialog } from "./LoginProviderFormDialog";

const editing: LoginProviderView = {
	registrationId: "gitlab-acme",
	type: "GITLAB",
	displayName: "ACME GitLab",
	baseUrl: "https://gitlab.acme.test",
	scopes: "read_user",
	enabled: true,
	seededFromEnv: false,
	redirectUri: "https://hephaestus.example.com/api/login/oauth2/code/gitlab-acme",
	createdAt: new Date("2026-05-02T00:00:00Z"),
	updatedAt: new Date("2026-05-02T00:00:00Z"),
};

const editingSlack: LoginProviderView = {
	registrationId: "slack",
	type: "SLACK",
	displayName: "Slack",
	baseUrl: "https://slack.com",
	scopes: "openid profile email",
	enabled: true,
	seededFromEnv: true,
	redirectUri: "https://hephaestus.example.com/api/login/oauth2/code/slack",
	createdAt: new Date("2026-05-02T00:00:00Z"),
	updatedAt: new Date("2026-05-02T00:00:00Z"),
};

const meta = {
	component: LoginProviderFormDialog,
	parameters: { layout: "centered" },
	args: {
		open: true,
		onOpenChange: fn(),
		editing: null,
		isSubmitting: false,
		onCreate: fn(),
		onUpdate: fn(),
	},
} satisfies Meta<typeof LoginProviderFormDialog>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Create mode: the registration ID is editable, and GitLab is selected so the base-URL field shows. */
export const Create: Story = {
	play: async () => {
		// Dialog renders in a portal → query the document.
		await expect(await screen.findByText("Add login provider")).toBeInTheDocument();
		await expect(screen.getByLabelText("Registration ID")).toBeEnabled();
		// GitLab is the default type, so the GitLab-only instance base URL field is present.
		await expect(screen.getByLabelText("Instance base URL")).toBeInTheDocument();
	},
};

export const EditSlack: Story = {
	args: { editing: editingSlack },
	play: async () => {
		await expect(await screen.findByText("Edit login provider")).toBeInTheDocument();
		await expect(screen.queryByLabelText("Instance base URL")).not.toBeInTheDocument();
		await expect(
			screen.getByText(/Use the same Slack app client ID and secret/),
		).toBeInTheDocument();
	},
};

/** Edit mode: the registration ID + type are the provider's immutable identity and are locked. */
export const Edit: Story = {
	args: { editing },
	play: async () => {
		await expect(await screen.findByText("Edit login provider")).toBeInTheDocument();
		await expect(screen.getByLabelText("Registration ID")).toBeDisabled();
	},
};
