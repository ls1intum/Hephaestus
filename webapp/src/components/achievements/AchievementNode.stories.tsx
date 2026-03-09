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
			{rarities.map((rarity) => {
				const unlockedProgressData = (() => {
					const raw = rarityMocks[rarity].progressData;
					if (!raw) {
						return { type: "BinaryAchievementProgress", unlocked: true } as const;
					}

					switch (raw.type) {
						case "LinearAchievementProgress":
							return { ...raw, current: raw.target } as const;
						case "BinaryAchievementProgress":
							return { ...raw, unlocked: true } as const;
						default:
							return { type: "BinaryAchievementProgress", unlocked: true } as const;
					}
				})();
				return (
					<div key={rarity} className="relative w-24 h-24 flex items-center justify-center">
						<AchievementNode
							id={`node-${rarity}`}
							data={{
								achievement: {
									...rarityMocks[rarity],
									status: "unlocked",
									unlockedAt: new Date(Date.now()),
									progressData: unlockedProgressData,
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
				);
			})}
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
			{rarities.map((rarity) => {
				const lockedProgressData = (() => {
					const raw = rarityMocks[rarity].progressData;
					if (!raw) {
						return { type: "BinaryAchievementProgress", unlocked: false } as const;
					}

					switch (raw.type) {
						case "LinearAchievementProgress":
							return { ...raw, current: 0 } as const;
						case "BinaryAchievementProgress":
							return { ...raw, unlocked: false } as const;
						default:
							return { type: "BinaryAchievementProgress", unlocked: false } as const;
					}
				})();
				return (
					<div key={rarity} className="relative w-24 h-24 flex items-center justify-center">
						<AchievementNode
							id={`node-locked-${rarity}`}
							data={{
								achievement: {
									...rarityMocks[rarity],
									status: "locked",
									progressData: lockedProgressData,
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
				);
			})}
		</div>
	),
};

/**
 * All rarities displayed with their standalone aura effect.
 * This is used for root nodes in the skill tree (achievements with no parents).
 */
export const StandaloneAuras: Story = {
	render: () => (
		<div className="flex items-center justify-center gap-12">
			{rarities.map((rarity) => (
				<div key={rarity} className="relative w-24 h-24 flex items-center justify-center">
					<AchievementNode
						id={`node-aura-${rarity}`}
						data={{
							achievement: {
								...rarityMocks[rarity],
								status: "unlocked",
							},
							showTooltips: true,
							forceAura: true,
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
 * All rarities displayed with their standalone aura effect while locked.
 * The aura is more subtle and less active for locked achievements.
 */
export const StandaloneAurasLocked: Story = {
	render: () => (
		<div className="flex items-center justify-center gap-12">
			{rarities.map((rarity) => (
				<div key={rarity} className="relative w-24 h-24 flex items-center justify-center">
					<AchievementNode
						id={`node-aura-locked-${rarity}`}
						data={{
							achievement: {
								...rarityMocks[rarity],
								status: "locked",
							},
							showTooltips: true,
							forceAura: true,
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
