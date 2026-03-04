import type { Meta, StoryObj } from "@storybook/react";
import { ReactFlowProvider } from "@xyflow/react";
import { SynthwaveEdge } from "./SynthwaveEdge";

/**
 * Storybook stories for SynthwaveEdge.
 *
 * Renders three overlapping neon sine-wave paths (cyan / magenta / purple)
 * that animate continuously with an equalizer-like effect.
 * Uses an inline SVG host so `<path>` and `<filter>` elements render correctly.
 */

const meta: Meta<typeof SynthwaveEdge> = {
	component: SynthwaveEdge,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component:
					"A synthwave-themed edge featuring three overlapping, animated neon sine-wave paths.\n\n" +
					"When enabled the waves flow continuously; when disabled the edge" +
					" falls back to a simple straight line.",
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
					<svg
						width={440}
						height={200}
						viewBox="0 0 440 200"
						xmlns="http://www.w3.org/2000/svg"
						role="img"
					>
						<title>Synthwave edge demo</title>
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
 * Comparison of Disabled (Muted Wire) vs Default (Neon Waves) vs Rarity themed states.
 */
export const States: Story = {
	render: () => (
		<>
			{/* Disabled State */}
			<SynthwaveEdge {...(createEdgeProps("disabled", 20, { isEnabled: false }) as any)} />
			<text x="40" y="10" className="fill-muted-foreground text-[10px] uppercase font-mono tracking-widest">
				Disabled
			</text>

			{/* Default Flowing State */}
			<SynthwaveEdge {...(createEdgeProps("default", 100, { isEnabled: true }) as any)} />
			<text x="40" y="90" className="fill-muted-foreground text-[10px] uppercase font-mono tracking-widest">
				Neon (Classic Flow)
			</text>

			{/* Rarity Variant */}
			<SynthwaveEdge
				{...(createEdgeProps("rarity", 180, { isEnabled: true, variant: "rarity" }) as any)}
			/>
			<text x="40" y="170" className="fill-muted-foreground text-[10px] uppercase font-mono tracking-widest">
				Rarity (Themed Waves)
			</text>
		</>
	),
};
