import type { Meta, StoryObj } from "@storybook/react";
import { Position, ReactFlowProvider } from "@xyflow/react";
import type { ComponentProps } from "react";
import { EqualizerEdge, type EqualizerVariant } from "@/components/achievements/EqualizerEdge";

/**
 * Storybook stories for EqualizerEdge.
 *
 * Renders a crisp base line acting as the wire, with an occasional neon
 * audio-mixer equalizer wave traveling across it.
 */

const meta = {
	component: EqualizerEdge,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component:
					"A hybrid edge displaying a straight line with sporadic, traveling 'audio-mixer' equalizer outbursts, replacing the regular particle animation.",
			},
			source: { state: "closed" },
		},
	},
	tags: ["autodocs"],
	decorators: [
		(Story) => (
			<ReactFlowProvider>
				<div className="bg-background w-[520px] p-12 flex justify-center">
					<svg
						width={440}
						height={200}
						viewBox="0 0 440 200"
						xmlns="http://www.w3.org/2000/svg"
						role="img"
					>
						<title>Equalizer Edge Demo</title>
						<Story />
					</svg>
				</div>
			</ReactFlowProvider>
		),
	],
} as Meta<typeof EqualizerEdge>;

export default meta;
type Story = StoryObj<typeof meta>;

type EqualizerData = {
	isEnabled: boolean;
	variant?: EqualizerVariant;
	depth?: number;
	maxDepth?: number;
};

const createEdgeProps = (
	id: string,
	y: number,
	data: EqualizerData = { isEnabled: false },
): ComponentProps<typeof EqualizerEdge> => ({
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

export const Default: Story = {
	args: createEdgeProps("default", 100, { isEnabled: true }),
};

/**
 * Comparison of Enabled (Traveling Neon) vs Disabled (Muted Wire) states.
 */
export const States: Story = {
	render: () => (
		<>
			{/* Disabled State */}
			<EqualizerEdge {...createEdgeProps("disabled", 20, { isEnabled: false })} />
			<text x="40" y="10" className="fill-muted-foreground text-[10px] uppercase font-mono">
				Disabled
			</text>

			{/* Enabled State (Traveling) */}
			<EqualizerEdge {...createEdgeProps("enabled", 100, { isEnabled: true })} />
			<text x="40" y="90" className="fill-muted-foreground text-[10px] uppercase font-mono">
				Enabled (Traveling)
			</text>

			{/* Static Variant */}
			<EqualizerEdge
				{...createEdgeProps("static", 180, {
					isEnabled: true,
					variant: "static",
				})}
			/>
			<text x="40" y="170" className="fill-muted-foreground text-[10px] uppercase font-mono">
				Static Variant
			</text>
		</>
	),
};
