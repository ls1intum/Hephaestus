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

// Mock data setups
const horizontalEnabled = {
	id: "eq-demo-h",
	sourceX: 30,
	sourceY: 100,
	targetX: 410,
	targetY: 100,
	data: { isEnabled: true },
};

const horizontalDisabled = {
	id: "eq-demo-h-off",
	sourceX: 30,
	sourceY: 40,
	targetX: 410,
	targetY: 40,
	data: { isEnabled: false },
};

const diagonalEnabled = {
	id: "eq-demo-diag",
	sourceX: 30,
	sourceY: 30,
	targetX: 410,
	targetY: 170,
	data: { isEnabled: true },
};

const staticHorizontalEnabled = {
	id: "eq-demo-static-h",
	sourceX: 30,
	sourceY: 100,
	targetX: 410,
	targetY: 100,
	data: { isEnabled: true, variant: "static" as const },
};

const staticDiagonalEnabled = {
	id: "eq-demo-static-diag",
	sourceX: 30,
	sourceY: 30,
	targetX: 410,
	targetY: 170,
	data: { isEnabled: true, variant: "static" as const },
};

const monochromeEnabled = {
	id: "eq-demo-mono-h",
	sourceX: 30,
	sourceY: 100,
	targetX: 410,
	targetY: 100,
	data: { isEnabled: true, monochrome: true },
};

const monochromeStaticEnabled = {
	id: "eq-demo-mono-static-h",
	sourceX: 30,
	sourceY: 100,
	targetX: 410,
	targetY: 100,
	data: { isEnabled: true, variant: "static" as const, monochrome: true },
};

export const Default: Story = {
	render: () => <EqualizerEdge {...(horizontalEnabled as any)} />,
	name: "Default",
};

export const Disabled: Story = {
	render: () => <EqualizerEdge {...(horizontalDisabled as any)} />,
	name: "Disabled",
};

export const Diagonal: Story = {
	render: () => <EqualizerEdge {...(diagonalEnabled as any)} />,
	name: "Diagonal",
};

export const Combined: Story = {
	render: () => (
		<>
			<EqualizerEdge {...(horizontalDisabled as any)} />
			<EqualizerEdge {...(horizontalEnabled as any)} />
			<EqualizerEdge {...(diagonalEnabled as any)} />
		</>
	),
	name: "Combined",
};

export const StaticDefault: Story = {
	render: () => <EqualizerEdge {...(staticHorizontalEnabled as any)} />,
	name: "Static Default",
};

export const StaticDiagonal: Story = {
	render: () => <EqualizerEdge {...(staticDiagonalEnabled as any)} />,
	name: "Static Diagonal",
};

export const TravelingMonochrome: Story = {
	render: () => <EqualizerEdge {...(monochromeEnabled as any)} />,
	name: "Traveling Monochrome",
};

export const StaticMonochrome: Story = {
	render: () => <EqualizerEdge {...(monochromeStaticEnabled as any)} />,
	name: "Static Monochrome",
};
