import type { Meta, StoryObj } from "@storybook/react";
import { GithubIcon } from "@/components/icons/brand";
import { Button } from "@/components/ui/button";
import { ConnectionHealthBadge } from "./ConnectionHealthBadge";
import { IntegrationPageHeader } from "./IntegrationPageHeader";

/**
 * The detail-page header for a single integration: brand icon, title, description and an optional
 * slot for status/actions (health badge, sync button). A layout primitive — the stories pin the
 * default, the with-actions composition, and long-text wrapping.
 */
const meta = {
	component: IntegrationPageHeader,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		icon: <GithubIcon className="size-6" />,
		title: "GitHub",
		description: "Repositories, sync state and job history for this workspace's GitHub connection.",
	},
} satisfies Meta<typeof IntegrationPageHeader>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const WithActions: Story = {
	args: {
		actions: (
			<>
				<ConnectionHealthBadge health="HEALTHY" />
				<Button size="sm" variant="outline">
					Sync now
				</Button>
			</>
		),
	},
};

/** A long title and description wrap gracefully rather than overflowing the header. */
export const LongText: Story = {
	args: {
		title: "GitHub Enterprise Cloud — Platform Engineering Organisation",
		description:
			"Repositories, sync state and job history for this workspace's GitHub connection, including backfill progress across every mirrored repository and the most recent webhook delivery.",
	},
};
