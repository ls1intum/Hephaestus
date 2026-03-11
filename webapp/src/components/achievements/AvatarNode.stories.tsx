import type { Meta, StoryObj } from "@storybook/react";
import { ReactFlowProvider } from "@xyflow/react";
import { AvatarNode } from "@/components/achievements/AvatarNode";

const meta = {
	component: AvatarNode,
	parameters: {
		layout: "centered",
		docs: { source: { state: "closed" } },
	},
	tags: ["autodocs"],
	decorators: [
		(Story) => (
			<ReactFlowProvider>
				<div className="p-12 flex justify-center">
					<Story />
				</div>
			</ReactFlowProvider>
		),
	],
} satisfies Meta<typeof AvatarNode>;

export default meta;
type Story = StoryObj<typeof meta>;

const defaultArgs = {
	data: {
		level: 42,
		leaguePoints: 1600,
		avatarUrl: "https://github.com/github.png",
		name: "Zeus",
	},
	id: "root-avatar",
	type: "avatar" as const,
	positionAbsoluteX: 0,
	positionAbsoluteY: 0,
	dragging: false,
	zIndex: 10,
	isConnectable: false,
	deletable: false,
	selectable: false,
	selected: false,
	draggable: false,
};

export const Default: Story = {
	args: defaultArgs,
};

export const HighLevel: Story = {
	args: {
		...defaultArgs,
		data: { ...defaultArgs.data, level: 99, leaguePoints: 4200, name: "Athena" },
	},
};

export const FallbackAvatar: Story = {
	args: {
		...defaultArgs,
		data: { ...defaultArgs.data, avatarUrl: "", name: "HP" },
	},
};
