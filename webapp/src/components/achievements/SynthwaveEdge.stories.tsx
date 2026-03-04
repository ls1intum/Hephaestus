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

// ---- Mock edge props ---- //

const horizontalEnabled = {
	id: "synthwave-demo-h",
	sourceX: 30,
	sourceY: 100,
	targetX: 410,
	targetY: 100,
	data: { isEnabled: true },
};

const horizontalDisabled = {
	id: "synthwave-demo-h-off",
	sourceX: 30,
	sourceY: 40,
	targetX: 410,
	targetY: 40,
	data: { isEnabled: false },
};

const diagonalEnabled = {
	id: "synthwave-demo-diag",
	sourceX: 30,
	sourceY: 30,
	targetX: 410,
	targetY: 170,
	data: { isEnabled: true },
};

const shortEnabled = {
	id: "synthwave-demo-short",
	sourceX: 140,
	sourceY: 100,
	targetX: 300,
	targetY: 100,
	data: { isEnabled: true },
};

// ---- Rarity variant mock data ---- //

const rarityHorizontal = {
	id: "synthwave-rarity-h",
	sourceX: 30,
	sourceY: 100,
	targetX: 410,
	targetY: 100,
	data: { isEnabled: true, variant: "rarity" as const },
};

const rarityDiagonal = {
	id: "synthwave-rarity-diag",
	sourceX: 30,
	sourceY: 30,
	targetX: 410,
	targetY: 170,
	data: { isEnabled: true, variant: "rarity" as const },
};

/** Default — enabled horizontal edge with flowing neon waves */
export const Default: Story = {
	render: () => <SynthwaveEdge {...(horizontalEnabled as any)} />,
	name: "Default",
};

/** Disabled — falls back to a subtle straight line */
export const Disabled: Story = {
	render: () => <SynthwaveEdge {...(horizontalDisabled as any)} />,
	name: "Disabled",
};

/** Diagonal — demonstrates the wave effect on an angled edge */
export const Diagonal: Story = {
	render: () => <SynthwaveEdge {...(diagonalEnabled as any)} />,
	name: "Diagonal",
};

/** Short — shorter edge to show scaled wave behavior */
export const Short: Story = {
	render: () => <SynthwaveEdge {...(shortEnabled as any)} />,
	name: "Short",
};

/** RarityDefault — rarity variant using uncommon (green), rare (blue), epic (purple) */
export const RarityDefault: Story = {
	render: () => <SynthwaveEdge {...(rarityHorizontal as any)} />,
	name: "Rarity Default",
};

/** RarityDiagonal — rarity variant on an angled edge */
export const RarityDiagonal: Story = {
	render: () => <SynthwaveEdge {...(rarityDiagonal as any)} />,
	name: "Rarity Diagonal",
};

/** VariantComparison — neon (top) vs rarity (bottom) side by side */
export const VariantComparison: Story = {
	render: () => (
		<>
			<SynthwaveEdge
				{...({
					id: "cmp-neon",
					sourceX: 30,
					sourceY: 70,
					targetX: 410,
					targetY: 70,
					data: { isEnabled: true, variant: "neon" },
				} as any)}
			/>
			<SynthwaveEdge
				{...({
					id: "cmp-rarity",
					sourceX: 30,
					sourceY: 140,
					targetX: 410,
					targetY: 140,
					data: { isEnabled: true, variant: "rarity" },
				} as any)}
			/>
		</>
	),
	name: "Variant Comparison",
};

/** Combined — all neon variants together for comparison */
export const Combined: Story = {
	render: () => (
		<>
			<SynthwaveEdge {...(horizontalDisabled as any)} />
			<SynthwaveEdge {...(horizontalEnabled as any)} />
			<SynthwaveEdge {...(diagonalEnabled as any)} />
		</>
	),
	name: "Combined",
};
