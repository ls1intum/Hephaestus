import type { Meta, StoryObj } from "@storybook/react";
import { ReactFlowProvider } from "@xyflow/react";
import { AchievementNode } from "@/components/achievements/AchievementNode";
import {
	apolloClarity,
	aresConflict,
	hephaestusInit,
	hermesSprint,
	poseidonTrident,
	zeusThunderbolt,
} from "./storyMockData";
import type { UIAchievement } from "./types";

/**
 * AchievementNode component for displaying achievements in the skill tree visualization.
 * Shows achievement icons with progress indicators, rarity styling, and tooltips.
 */
const meta = {
	component: AchievementNode,
	title: "Achievements/AchievementNode",
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component: "Displays achievement nodes in the skill tree with digital mythological themes.",
			},
			source: {
				state: "closed",
			},
		},
	},
	tags: ["autodocs"],
	decorators: [
		(Story) => (
			<ReactFlowProvider>
				<div
					className="bg-background p-12"
					style={{
						paddingTop: "240px",
						paddingBottom: "120px",
						display: "flex",
						justifyContent: "center",
					}}
				>
					<Story />
				</div>
			</ReactFlowProvider>
		),
	],
} satisfies Meta<typeof AchievementNode>;

export default meta;
type Story = StoryObj<typeof AchievementNode>;

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

const rarities = ["common", "uncommon", "rare", "epic", "legendary", "mythic"] as const;

const rarityMocks: Record<(typeof rarities)[number], UIAchievement> = {
	common: hephaestusInit,
	uncommon: hermesSprint,
	rare: aresConflict,
	epic: apolloClarity,
	legendary: poseidonTrident,
	mythic: zeusThunderbolt,
};

/**
 * All rarities displayed as unlocked.
 */
export const Unlocked: Story = {
	render: () => (
		<div className="flex items-center justify-center gap-12">
			{rarities.map((rarity) => (
				<div key={rarity} className="relative w-24 h-24 flex items-center justify-center">
					<AchievementNode
						id={`node-${rarity}`}
						data={{
							achievement: { ...rarityMocks[rarity], status: "unlocked" },
							showTooltips: true,
						}}
						{...sharedNodeProps}
					/>
					<div className="absolute -bottom-8 left-1/2 -translate-x-1/2 whitespace-nowrap">
						<span className="text-[10px] text-muted-foreground uppercase font-mono tracking-tighter">
							{rarity}
						</span>
					</div>
				</div>
			))}
		</div>
	),
};

/**
 * All rarities displayed as available for progress.
 * Each node shows randomized progress toward its goal.
 */
export const Available: Story = {
	render: () => (
		<div className="flex items-center justify-center gap-12">
			{rarities.map((rarity, index) => (
				<div key={rarity} className="relative w-24 h-24 flex items-center justify-center">
					<AchievementNode
						id={`node-available-${rarity}`}
						data={{
							achievement: {
								...rarityMocks[rarity],
								status: "available",
								progressData: {
									type: "LinearAchievementProgress",
									current: (index + 1) * 15,
									target: 100,
								},
							},
							showTooltips: true,
						}}
						{...sharedNodeProps}
					/>
					<div className="absolute -bottom-8 left-1/2 -translate-x-1/2 whitespace-nowrap">
						<span className="text-[10px] text-muted-foreground uppercase font-mono tracking-tighter">
							{rarity}
						</span>
					</div>
				</div>
			))}
		</div>
	),
};

/**
 * All rarities displayed as locked (monochromatic).
 */
export const Locked: Story = {
	render: () => (
		<div className="flex items-center justify-center gap-12">
			{rarities.map((rarity) => (
				<div key={rarity} className="relative w-24 h-24 flex items-center justify-center">
					<AchievementNode
						id={`node-locked-${rarity}`}
						data={{
							achievement: { ...rarityMocks[rarity], status: "locked" },
							showTooltips: true,
						}}
						{...sharedNodeProps}
					/>
					<div className="absolute -bottom-8 left-1/2 -translate-x-1/2 whitespace-nowrap">
						<span className="text-[10px] text-muted-foreground uppercase font-mono tracking-tighter">
							{rarity}
						</span>
					</div>
				</div>
			))}
		</div>
	),
};
