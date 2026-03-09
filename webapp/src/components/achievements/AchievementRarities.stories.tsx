import type { Meta, StoryObj } from "@storybook/react";
import { ReactFlowProvider } from "@xyflow/react";
import { AchievementNode } from "@/components/achievements/AchievementNode";
import type { UIAchievement } from "@/components/achievements/types";
import {
	apolloClarity,
	aresConflict,
	hephaestusInit,
	hermesSprint,
	poseidonTrident,
	zeusThunderbolt,
} from "./storyMockData";

/**
 * Showcases all six achievement rarity tiers side by side using mythic-themed artifacts.
 *
 * Each tier demonstrates the progressive border system:
 * - **Common**: thin gray border — the baseline.
 * - **Uncommon**: green border — first hint of color.
 * - **Rare**: blue border + faint glow.
 * - **Epic**: purple border + layered glow.
 * - **Legendary**: gold border + animated pulsing glow.
 * - **Mythic**: rotating conic-gradient border with divine energy.
 */

const sharedNodeProps = {
	type: "achievement" as const,
	dragging: false,
	zIndex: 0,
	isConnectable: false,
	positionAbsoluteX: 0,
	positionAbsoluteY: 0,
	deletable: false,
	selectable: false,
	parentId: undefined,
	sourcePosition: undefined,
	targetPosition: undefined,
	selected: false,
	draggable: false,
	width: undefined,
	height: undefined,
};

const meta = {
	component: AchievementNode,
	// title: "Achievements/Rarity Showcase",
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component:
					"All six rarity tiers displayed in both unlocked and locked states. " +
					"The border system uses progressive visual enhancement: " +
					"width, color, glow, and animation increase with each tier.",
			},
			source: { state: "closed" },
		},
	},
	tags: ["autodocs"],
	decorators: [
		(Story) => (
			<ReactFlowProvider>
				<div className="bg-background p-32 flex justify-center items-center min-h-[400px]">
					<Story />
				</div>
			</ReactFlowProvider>
		),
	],
} satisfies Meta<typeof AchievementNode>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Common rarity — the baseline divine spark.
 */
export const Common: Story = {
	args: {
		id: "rarity-common",
		data: { achievement: { ...hephaestusInit, status: "unlocked" } as UIAchievement },
		...sharedNodeProps,
	},
};

/**
 * Uncommon rarity — a refined artifact.
 */
export const Uncommon: Story = {
	args: {
		id: "rarity-uncommon",
		data: { achievement: { ...hermesSprint, status: "unlocked" } as UIAchievement },
		...sharedNodeProps,
	},
};

/**
 * Rare rarity — a glowing relic of the gods.
 */
export const Rare: Story = {
	args: {
		id: "rarity-rare",
		data: { achievement: { ...aresConflict, status: "unlocked" } as UIAchievement },
		...sharedNodeProps,
	},
};

/**
 * Epic rarity — an artifact of immense power.
 */
export const Epic: Story = {
	args: {
		id: "rarity-epic",
		data: { achievement: { ...apolloClarity, status: "unlocked" } as UIAchievement },
		...sharedNodeProps,
	},
};

/**
 * Legendary rarity — a pulsing divine instrument.
 */
export const Legendary: Story = {
	args: {
		id: "rarity-legendary",
		data: { achievement: { ...poseidonTrident, status: "unlocked" } as UIAchievement },
		...sharedNodeProps,
	},
};

/**
 * Mythic rarity — the ultimate artifact of the forge.
 */
export const Mythic: Story = {
	args: {
		id: "rarity-mythic",
		data: { achievement: { ...zeusThunderbolt, status: "unlocked" } as UIAchievement },
		...sharedNodeProps,
	},
};
