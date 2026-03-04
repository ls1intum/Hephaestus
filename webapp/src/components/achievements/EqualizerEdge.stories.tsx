import type { Meta, StoryObj } from "@storybook/react";
import { ReactFlowProvider } from "@xyflow/react";
import { EqualizerEdge } from "./EqualizerEdge";

/**
 * Storybook stories for EqualizerEdge.
 *
 * Renders a crisp base line acting as the wire, with an occasional neon
 * audio-mixer equalizer wave traveling across it.
 */

const meta: Meta<typeof EqualizerEdge> = {
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
				<div
					className="bg-background"
					style={{ width: 520, padding: 48, display: "flex", justifyContent: "center" }}
				>
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
};

export default meta;
type Story = StoryObj<typeof meta>;

// Mock edge props generator
const createEdgeProps = (id: string, y: number, data: any = {}) => ({
	id,
	sourceX: 40,
	sourceY: y,
	targetX: 400,
	targetY: y,
	data,
	sourcePosition: "right" as const,
	targetPosition: "left" as const,
});

export const Default: Story = {
	args: createEdgeProps("default", 100, { isEnabled: true }) as any,
};

/**
 * Comparison of Enabled (Traveling Neon) vs Disabled (Muted Wire) states.
 */
export const States: Story = {
	render: () => (
		<>
			{/* Disabled State */}
			<EqualizerEdge {...(createEdgeProps("disabled", 20, { isEnabled: false }) as any)} />
			<text x="40" y="10" className="fill-muted-foreground text-[10px] uppercase font-mono">
				Disabled
			</text>

			{/* Enabled State (Traveling) */}
			<EqualizerEdge {...(createEdgeProps("enabled", 100, { isEnabled: true }) as any)} />
			<text x="40" y="90" className="fill-muted-foreground text-[10px] uppercase font-mono">
				Enabled (Traveling)
			</text>

			{/* Static Variant */}
			<EqualizerEdge
				{...(createEdgeProps("static", 180, {
					isEnabled: true,
					variant: "static",
				}) as any)}
			/>
			<text x="40" y="170" className="fill-muted-foreground text-[10px] uppercase font-mono">
				Static Variant
			</text>
		</>
	),
};
