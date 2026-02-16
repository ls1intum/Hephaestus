import type { Meta, StoryObj } from "@storybook/react";
import { ReactFlowProvider } from "@xyflow/react";
import { AchievementEdge } from "./AchievementEdge";

/**
 * Storybook stories for AchievementEdge.
 * Uses a small inline SVG to host the edge path and animated particle.
 * Mock data follows the project's playful digital-Greek-mythology theme.
 */

const meta: Meta<typeof AchievementEdge> = {
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
				<div
					className="dark bg-background"
					style={{ width: 520, padding: 48, display: "flex", justifyContent: "center" }}
				>
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
};

export default meta;
type Story = StoryObj<typeof meta>;

// Mock edges using the project's digital-Greek-mythology naming
// These are deliberately non-trivial positions to show the straight-line path
const zeusEdgeEnabled = {
	sourceX: 40,
	sourceY: 90,
	targetX: 400,
	targetY: 90,
	data: { isEnabled: true },
};

const zeusEdgeDisabled = {
	sourceX: 40,
	sourceY: 40,
	targetX: 400,
	targetY: 40,
	data: { isEnabled: false },
};

const hermesEdgeAnimated = {
	sourceX: 40,
	sourceY: 140,
	targetX: 400,
	targetY: 140,
	data: { isEnabled: true },
};

// In-between example: two segments that together form a full connection
// left segment is enabled, right segment is disabled — visually represents
// an "in-between" progress state along the connection.
const inBetweenLeft = {
	sourceX: 40,
	sourceY: 115,
	targetX: 220,
	targetY: 115,
	data: { isEnabled: true },
};

const inBetweenRight = {
	sourceX: 220,
	sourceY: 115,
	targetX: 400,
	targetY: 115,
	data: { isEnabled: false },
};

/** Default - enabled edge (represents the default/active state) */
export const Default: Story = {
	render: () => <AchievementEdge {...(zeusEdgeEnabled as any)} />,
	name: "Default",
};

/** Active - animated edge to demonstrate particle motion */
export const Active: Story = {
	render: () => <AchievementEdge {...(hermesEdgeAnimated as any)} />,
	name: "Active",
};

/** Disabled - inactive edge (no glow or particle) */
export const Disabled: Story = {
	render: () => <AchievementEdge {...(zeusEdgeDisabled as any)} />,
	name: "Disabled",
};

/** Partial - segmented edge demonstrating an in-between state (partial activation) */
export const Partial: Story = {
	render: () => (
		<>
			<AchievementEdge {...(inBetweenLeft as any)} />
			<AchievementEdge {...(inBetweenRight as any)} />
		</>
	),
	name: "Partial",
};
