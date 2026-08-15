import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, waitFor, within } from "storybook/test";
import { CatalogOriginBadge } from "./CatalogOriginBadge";

const meta = {
	title: "Workspace admin/Practices/Catalog status",
	component: CatalogOriginBadge,
	tags: ["autodocs"],
	args: {
		kind: "practice",
		origin: { slug: "clear-pr-description", link: "IN_SYNC", sourceOffered: true },
	},
} satisfies Meta<typeof CatalogOriginBadge>;

export default meta;
type Story = StoryObj<typeof meta>;

export const MatchesCatalog: Story = {
	play: async ({ canvasElement }) => {
		await expect(within(canvasElement).queryByText(/catalog/)).not.toBeInTheDocument();
	},
};

export const CatalogChanged: Story = {
	args: {
		origin: {
			slug: "clear-pr-description",
			link: "UPDATE_AVAILABLE",
			sourceOffered: true,
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		canvas.getByRole("button", { name: "Instance catalog changed" }).focus();
		const tooltip = await within(document.body).findByText(
			"The instance catalog now has different review rules. This workspace keeps its current version.",
		);
		await waitFor(() => expect(tooltip).toBeVisible());
	},
};

export const Customized: Story = {
	args: {
		origin: {
			slug: "clear-pr-description",
			link: "LOCALLY_EDITED",
			sourceOffered: true,
		},
	},
};

export const NoLongerIncluded: Story = {
	args: {
		origin: {
			slug: "clear-pr-description",
			link: "IN_SYNC",
			sourceOffered: false,
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const status = canvas.getByRole("button", {
			name: "Not in the current instance catalog",
		});
		status.focus();
		const tooltip = await within(document.body).findByText(
			"New workspaces no longer receive this practice from the instance catalog. This workspace keeps its version.",
		);
		await waitFor(() => expect(tooltip).toBeVisible());
	},
};

export const AreaChanged: Story = {
	args: {
		kind: "area",
		origin: {
			slug: "communication",
			link: "UPDATE_AVAILABLE",
			sourceOffered: true,
		},
	},
	play: async ({ canvasElement }) => {
		const status = within(canvasElement).getByRole("button", {
			name: "Instance catalog changed",
		});
		status.focus();
		const tooltip = await within(document.body).findByText(
			"The instance catalog now has different area details. This workspace keeps its current version.",
		);
		await waitFor(() => expect(tooltip).toBeVisible());
	},
};
