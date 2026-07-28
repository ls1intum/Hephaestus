import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent } from "storybook/test";
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

const editingOutline: LoginProviderView = {
	registrationId: "outline-acme",
	type: "OUTLINE",
	displayName: "ACME Outline",
	baseUrl: "https://outline.acme.test",
	scopes: "read",
	enabled: true,
	seededFromEnv: false,
	redirectUri: "https://hephaestus.example.com/api/login/oauth2/code/outline-acme",
	createdAt: new Date("2026-07-02T00:00:00Z"),
	updatedAt: new Date("2026-07-02T00:00:00Z"),
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
		// GitLab is the default type, so the instance base URL field is present.
		await expect(screen.getByLabelText("Instance base URL")).toBeInTheDocument();
	},
};

/** Every provider type the server accepts is offered — including OUTLINE, which is link-only. */
export const CreateOutline: Story = {
	play: async () => {
		await userEvent.click(await screen.findByRole("combobox", { name: "Provider type" }));
		await userEvent.click(await screen.findByRole("option", { name: /Outline/i }));

		// Outline is self-hosted per instance, so (like GitLab) it carries a base URL...
		await expect(screen.getByLabelText("Instance base URL")).toBeInTheDocument();
		// ...and the admin is told it is link-only, plus which redirect URI to register in Outline.
		await expect(screen.getByText(/nobody signs in to Hephaestus with it/i)).toBeInTheDocument();
		await expect(screen.getByText(/Settings → Applications/)).toBeInTheDocument();
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

/** Edit an Outline provider: base URL kept, registration ID + type locked, redirect URI shown. */
export const EditOutline: Story = {
	args: { editing: editingOutline },
	play: async () => {
		await expect(await screen.findByText("Edit login provider")).toBeInTheDocument();
		await expect(screen.getByLabelText("Registration ID")).toBeDisabled();
		await expect(screen.getByLabelText("Instance base URL")).toHaveValue(
			"https://outline.acme.test",
		);
		await expect(screen.getByText(editingOutline.redirectUri)).toBeInTheDocument();
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
