import type { Meta, StoryObj } from "@storybook/react";
import { Position, ReactFlowProvider } from "@xyflow/react";
import type { ComponentProps } from "react";
import { AchievementEdge } from "@/components/achievements/AchievementEdge";

/**
 * Storybook stories for AchievementEdge.
 * Uses a small inline SVG to host the edge path and animated particle.
 * Mock data follows the project's playful digital-Greek-mythology theme.
 */

const meta = {
	component: AchievementEdge,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component:
					"Renders connecting edges between achievement nodes in the skill tree. Uses glow + particle animation for active edges.\n\nStories are named to follow the project's default state representation convention: `Default`, `Active`, `Disabled`, and `Partial`.",
			},
			source: { state: "closed" },
		},
	},
	tags: ["autodocs"],
	decorators: [
		(Story) => (
			<ReactFlowProvider>
				<div className="bg-background w-[520px] p-12 flex justify-center">
					{/* Provide an SVG viewport so <path>, <circle> and <animateMotion> render correctly */}
					<svg
						width={440}
						height={180}
						viewBox={`0 0 440 180`}
						xmlns="http://www.w3.org/2000/svg"
						role="img"
					>
						<title>Achievement edge demo (digital mythology)</title>
						<Story />
					</svg>
				</div>
			</ReactFlowProvider>
		),
	],
} as Meta<typeof AchievementEdge>;

export default meta;
type Story = StoryObj<typeof meta>;

const createEdgeProps = (
	id: string,
	y: number,
	data: { isEnabled: boolean },
): ComponentProps<typeof AchievementEdge> => ({
	id,
	source: "source-node",
	target: "target-node",
	sourceX: 40,
	sourceY: y,
	targetX: 400,
	targetY: y,
	data,
	sourcePosition: Position.Right,
	targetPosition: Position.Left,
});

const zeusEdgeDisabled = createEdgeProps("zeus-disabled", 40, { isEnabled: false });
const hermesEdgeAnimated = createEdgeProps("hermes-animated", 140, { isEnabled: true });

/** Active - animated edge to demonstrate particle motion */
export const Active: Story = {
	render: () => <AchievementEdge {...hermesEdgeAnimated} />,
	name: "Active",
};

/** Disabled (Default state) - inactive edge (no glow or particle) */
export const Disabled: Story = {
	render: () => <AchievementEdge {...zeusEdgeDisabled} />,
	name: "Disabled",
};
