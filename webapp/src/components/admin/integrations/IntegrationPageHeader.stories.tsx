import type { Meta, StoryObj } from "@storybook/react";
import { GithubIcon } from "@/components/icons/brand";
import { Button } from "@/components/ui/button";
import { ConnectionHealthBadge } from "./ConnectionHealthBadge";
import { IntegrationPageHeader } from "./IntegrationPageHeader";

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
