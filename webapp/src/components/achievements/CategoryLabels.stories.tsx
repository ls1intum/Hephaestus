import type { Meta, StoryObj } from "@storybook/react";
import { ReactFlowProvider } from "@xyflow/react";
import { CategoryLabelNode } from "@/components/achievements/CategoryLabels";

const meta = {
	component: CategoryLabelNode,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component: "A React Flow custom node representing a category label.",
			},
		},
	},
	tags: ["autodocs"],
	decorators: [
		(Story) => (
			<div className="p-12">
				<ReactFlowProvider>
					<Story />
				</ReactFlowProvider>
			</div>
		),
	],
} satisfies Meta<typeof CategoryLabelNode>;

export default meta;
type Story = StoryObj<typeof meta>;

// Basic
export const PullRequests: Story = {
	args: {
		id: "pull-requests",
		type: "categoryLabel",
		data: {
			category: "pull_requests",
			name: "Pull Requests",
		},
		selected: false,
		zIndex: 1,
		dragging: false,
		positionAbsoluteX: 0,
		positionAbsoluteY: 0,
		isConnectable: false,
		draggable: false,
		selectable: false,
		deletable: false,
	},
};

export const Commits: Story = {
	args: {
		id: "commits",
		type: "categoryLabel",
		data: {
			category: "commits",
			name: "Commits",
		},
		selected: false,
		zIndex: 1,
		dragging: false,
		positionAbsoluteX: 0,
		positionAbsoluteY: 0,
		isConnectable: false,
		draggable: false,
		selectable: false,
		deletable: false,
	},
};
