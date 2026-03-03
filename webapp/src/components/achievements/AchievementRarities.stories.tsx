import {
	CheckCircleIcon,
	CodeIcon,
	CommentIcon,
	GitCommitIcon,
	GitPullRequestIcon,
	IssueOpenedIcon,
} from "@primer/octicons-react";
import type { Meta, StoryObj } from "@storybook/react";
import { ReactFlowProvider } from "@xyflow/react";
import { AchievementNode } from "@/components/achievements/AchievementNode";
import { rarityLabels } from "@/components/achievements/styles";
import type { AchievementRarity, UIAchievement } from "@/components/achievements/types";

/**
 * Showcases all six achievement rarity tiers side by side.
 *
 * Each tier demonstrates the progressive border system:
 * - **Common**: thin gray border — the baseline.
 * - **Uncommon**: green border — first hint of color.
 * - **Rare**: blue border + faint glow.
 * - **Epic**: purple border + layered glow.
 * - **Legendary**: gold border + animated pulsing glow.
 * - **Mythic**: rotating conic-gradient border.
 */

// ===== Mock data per rarity ===== //

const mockByRarity: Record<AchievementRarity, UIAchievement> = {
	common: {
		id: "first_pull",
		name: "First Pull",
		description: "Open your first pull request.",
		icon: GitPullRequestIcon,
		category: "pull_requests",
		rarity: "common",
		status: "unlocked",
		unlockedAt: new Date("2025-01-10"),
		progressData: { type: "BinaryAchievementProgress", unlocked: true },
	} as unknown as UIAchievement,

	uncommon: {
		id: "pr_beginner",
		name: "Beginner Integrator",
		description: "Merge 3 Pull Requests.",
		icon: GitCommitIcon,
		category: "commits",
		rarity: "uncommon",
		status: "unlocked",
		unlockedAt: new Date("2025-02-15"),
		progressData: { type: "LinearAchievementProgress", current: 3, target: 3 },
	} as unknown as UIAchievement,

	rare: {
		id: "pr_apprentice",
		name: "Apprentice Integrator",
		description: "Merge 5 Pull Requests.",
		icon: IssueOpenedIcon,
		category: "issues",
		rarity: "rare",
		status: "unlocked",
		unlockedAt: new Date("2025-03-20"),
		progressData: { type: "LinearAchievementProgress", current: 5, target: 5 },
	} as unknown as UIAchievement,

	epic: {
		id: "first_review",
		name: "First Review",
		description: "Submit your first code review.",
		icon: CommentIcon,
		category: "communication",
		rarity: "epic",
		status: "unlocked",
		unlockedAt: new Date("2025-04-05"),
		progressData: { type: "BinaryAchievementProgress", unlocked: true },
	} as unknown as UIAchievement,

	legendary: {
		id: "review_master",
		name: "Review Master",
		description: "Submit 100 code reviews.",
		icon: CheckCircleIcon,
		category: "communication",
		rarity: "legendary",
		status: "unlocked",
		unlockedAt: new Date("2025-06-01"),
		progressData: { type: "LinearAchievementProgress", current: 100, target: 100 },
	} as unknown as UIAchievement,

	mythic: {
		id: "code_commenter",
		name: "Code Commenter",
		description: "Post 100 code comments.",
		icon: CodeIcon,
		category: "communication",
		rarity: "mythic",
		status: "unlocked",
		unlockedAt: new Date("2025-08-15"),
		progressData: { type: "LinearAchievementProgress", current: 100, target: 100 },
	} as unknown as UIAchievement,
};

// Shared node props boilerplate for React Flow NodeProps
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
	title: "Achievements/Rarity Showcase",
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
				<div
					className="bg-background p-12"
					style={{
						paddingTop: "200px",
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
type Story = StoryObj<typeof meta>;

// ===== Individual rarity stories ===== //

/** Common — thin gray border, no effects. */
export const Common: Story = {
	args: {
		id: mockByRarity.common.id,
		data: { achievement: mockByRarity.common },
		...sharedNodeProps,
	},
};

/** Uncommon — green border. */
export const Uncommon: Story = {
	args: {
		id: mockByRarity.uncommon.id,
		data: { achievement: mockByRarity.uncommon },
		...sharedNodeProps,
	},
};

/** Rare — blue border with faint glow. */
export const Rare: Story = {
	args: { id: mockByRarity.rare.id, data: { achievement: mockByRarity.rare }, ...sharedNodeProps },
};

/** Epic — purple border with layered glow. */
export const Epic: Story = {
	args: { id: mockByRarity.epic.id, data: { achievement: mockByRarity.epic }, ...sharedNodeProps },
};

/** Legendary — gold border with animated pulsing glow. */
export const Legendary: Story = {
	args: {
		id: mockByRarity.legendary.id,
		data: { achievement: mockByRarity.legendary },
		...sharedNodeProps,
	},
};

/** Mythic — rotating conic-gradient border. */
export const Mythic: Story = {
	args: {
		id: mockByRarity.mythic.id,
		data: { achievement: mockByRarity.mythic },
		...sharedNodeProps,
	},
};

// ===== Composite stories ===== //

const rarityOrder: AchievementRarity[] = [
	"common",
	"uncommon",
	"rare",
	"epic",
	"legendary",
	"mythic",
];

/** All rarities side by side — unlocked state. */
export const AllRaritiesUnlocked: Story = {
	args: {
		id: mockByRarity.common.id,
		data: { achievement: mockByRarity.common },
		...sharedNodeProps,
	},
	render: () => (
		<ReactFlowProvider>
			<div className="bg-background p-8 flex items-end gap-8" style={{ paddingTop: "200px" }}>
				{rarityOrder.map((rarity) => (
					<div key={rarity} className="flex flex-col items-center gap-3">
						<AchievementNode
							id={mockByRarity[rarity].id}
							data={{ achievement: mockByRarity[rarity] }}
							{...sharedNodeProps}
						/>
						<span className="text-xs text-muted-foreground font-medium capitalize">
							{rarityLabels[rarity]}
						</span>
					</div>
				))}
			</div>
		</ReactFlowProvider>
	),
};

/** All rarities side by side — locked state. Shows grayed-out fills with rarity borders preserved. */
export const AllRaritiesLocked: Story = {
	args: {
		id: mockByRarity.common.id,
		data: { achievement: mockByRarity.common },
		...sharedNodeProps,
	},
	render: () => (
		<ReactFlowProvider>
			<div className="bg-background p-8 flex items-end gap-8" style={{ paddingTop: "200px" }}>
				{rarityOrder.map((rarity) => {
					const locked: UIAchievement = {
						...mockByRarity[rarity],
						status: "locked",
						unlockedAt: null,
					} as unknown as UIAchievement;
					return (
						<div key={rarity} className="flex flex-col items-center gap-3">
							<AchievementNode id={locked.id} data={{ achievement: locked }} {...sharedNodeProps} />
							<span className="text-xs text-muted-foreground font-medium capitalize">
								{rarityLabels[rarity]}
							</span>
						</div>
					);
				})}
			</div>
		</ReactFlowProvider>
	),
};

/** Side-by-side comparison: locked vs unlocked for each rarity. */
export const LockedVsUnlocked: Story = {
	args: {
		id: mockByRarity.common.id,
		data: { achievement: mockByRarity.common },
		...sharedNodeProps,
	},
	render: () => (
		<ReactFlowProvider>
			<div className="bg-background p-8" style={{ paddingTop: "200px" }}>
				<div className="grid grid-cols-6 gap-8">
					{/* Unlocked row */}
					{rarityOrder.map((rarity) => (
						<div key={`unlocked-${rarity}`} className="flex flex-col items-center gap-2">
							<AchievementNode
								id={mockByRarity[rarity].id}
								data={{ achievement: mockByRarity[rarity] }}
								{...sharedNodeProps}
							/>
							<span className="text-[10px] text-foreground font-medium">Unlocked</span>
						</div>
					))}
					{/* Locked row */}
					{rarityOrder.map((rarity) => {
						const locked: UIAchievement = {
							...mockByRarity[rarity],
							status: "locked",
							unlockedAt: null,
						} as unknown as UIAchievement;
						return (
							<div key={`locked-${rarity}`} className="flex flex-col items-center gap-2">
								<AchievementNode
									id={locked.id}
									data={{ achievement: locked }}
									{...sharedNodeProps}
								/>
								<span className="text-[10px] text-muted-foreground font-medium">Locked</span>
							</div>
						);
					})}
					{/* Labels row */}
					{rarityOrder.map((rarity) => (
						<div key={`label-${rarity}`} className="flex justify-center">
							<span className="text-xs text-muted-foreground font-semibold capitalize">
								{rarityLabels[rarity]}
							</span>
						</div>
					))}
				</div>
			</div>
		</ReactFlowProvider>
	),
};
