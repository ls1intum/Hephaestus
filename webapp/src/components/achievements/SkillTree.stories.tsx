import type { Meta, StoryObj } from "@storybook/react";
import { ReactFlow, ReactFlowProvider } from "@xyflow/react";
import {
	Cpu,
	Database,
	GitCommit,
	GitPullRequest,
	Globe,
	Shield,
	Terminal,
	Zap,
} from "lucide-react";
import { SkillTree } from "./SkillTree";
import type { UIAchievement } from "./types";
import { generateSkillTreeData } from "./utils";

// Modern digital mythology mock user
const mockUser = {
	name: "Hephaestus_Dev",
	avatarUrl: "https://github.com/github.png",
	level: 42,
	leaguePoints: 9001,
};

// Achievements themed with Greek-tech names and non-trivial progress numbers
const mythicAchievements: UIAchievement[] = [
	{
		id: "first_pull",
		name: "Hephaestus's Spark",
		description: "Initialize the repository forge with the first commit.",
		category: "commits",
		rarity: "common",
		status: "unlocked",
		icon: Terminal,
		unlockedAt: new Date("2024-01-01"),
		progressData: { type: "LinearAchievementProgress", current: 1, target: 1 },
	} as const satisfies UIAchievement,
	{
		id: "hephaestus-init",
		name: "Hephaestus's Spark",
		description: "Initialize the repository forge with the first commit.",
		category: "commits",
		rarity: "common",
		status: "unlocked",
		icon: Terminal,
		progress: 1,
		maxProgress: 1,
		unlockedAt: new Date("2024-01-01"),
		progressData: { type: "BinaryAchievementProgress", unlocked: true },
	} as unknown as UIAchievement,
	{
		id: "hephaestus-hammer",
		name: "Hammer of CI/CD",
		description: "Forge 50 automated builds without a single failure.",
		category: "commits",
		rarity: "rare",
		parentId: "hephaestus-init",
		status: "unlocked",
		icon: Cpu,
		progress: 50,
		maxProgress: 50,
		unlockedAt: new Date("2024-02-15"),
		progressData: { type: "LinearAchievementProgress", current: 50, target: 50 },
	} as unknown as UIAchievement,
	{
		id: "hephaestus-automaton",
		name: "Golden Automaton",
		description: "Create a self-healing deployment script.",
		category: "commits",
		rarity: "legendary",
		parentId: "hephaestus-hammer",
		status: "available",
		icon: Zap,
		progress: 2,
		maxProgress: 5,
		unlockedAt: null,
		progressData: { type: "LinearAchievementProgress", current: 2, target: 5 },
	} as unknown as UIAchievement,

	{
		id: "hermes-sprint",
		name: "Hermes's Hotfix",
		description: "Deliver a hotfix PR in under 10 minutes from issue creation.",
		category: "pull_requests",
		rarity: "uncommon",
		status: "unlocked",
		icon: GitPullRequest,
		progress: 1,
		maxProgress: 1,
		unlockedAt: new Date("2024-03-10"),
		progressData: { type: "BinaryAchievementProgress", unlocked: true },
	} as unknown as UIAchievement,
	{
		id: "hermes-caduceus",
		name: "Caduceus Merger",
		description: "Merge 100 PRs without causing a regression.",
		category: "pull_requests",
		rarity: "epic",
		parentId: "hermes-sprint",
		status: "locked",
		icon: Globe,
		progress: 45,
		maxProgress: 100,
		unlockedAt: null,
		progressData: { type: "LinearAchievementProgress", current: 45, target: 100 },
	} as unknown as UIAchievement,

	{
		id: "athena-review",
		name: "Owl's Eye Review",
		description: "Provide constructive feedback on 10 junior developer PRs.",
		category: "communication",
		rarity: "rare",
		status: "unlocked",
		icon: GitCommit,
		progress: 10,
		maxProgress: 10,
		unlockedAt: new Date("2024-01-20"),
		progressData: { type: "LinearAchievementProgress", current: 10, target: 10 },
	} as unknown as UIAchievement,
	{
		id: "athena-strategy",
		name: "Architecture Aegis",
		description: "Defend the system architecture in a design review.",
		category: "communication",
		rarity: "mythic",
		parentId: "athena-review",
		status: "locked",
		icon: Shield,
		progress: 0,
		maxProgress: 1,
		unlockedAt: null,
		progressData: { type: "BinaryAchievementProgress", unlocked: false },
	} as unknown as UIAchievement,

	{
		id: "zeus-thunderbolt",
		name: "Thunderbolt Bug Squash",
		description: "Close a critical issue with highest priority.",
		category: "issues",
		rarity: "epic",
		status: "available",
		icon: Zap,
		progress: 0,
		maxProgress: 1,
		unlockedAt: null,
		progressData: { type: "BinaryAchievementProgress", unlocked: false },
	} as unknown as UIAchievement,

	{
		id: "poseidon-trident",
		name: "Trident Release",
		description: "Successfully ship 3 major versions.",
		category: "milestones",
		rarity: "legendary",
		status: "unlocked",
		icon: Database,
		progress: 3,
		maxProgress: 3,
		unlockedAt: new Date("2024-04-01"),
		progressData: { type: "LinearAchievementProgress", current: 3, target: 3 },
	} as unknown as UIAchievement,
];

const meta: Meta<typeof SkillTree> = {
	component: SkillTree,
	parameters: {
		layout: "fullscreen",
		docs: { source: { state: "closed" } },
	},
	tags: ["autodocs"],
	decorators: [
		(Story) => (
			<ReactFlowProvider>
				<div className="h-screen w-full bg-background">
					{/* Add top padding so node tooltips aren't clipped in docs. Ensure a height for the inner container so React Flow fills it. */}
					<div style={{ paddingTop: 160, height: "calc(100vh - 160px)" }}>
						<Story />
					</div>
				</div>
			</ReactFlowProvider>
		),
	],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	args: {
		user: mockUser,
		achievements: mythicAchievements,
	},
};

export const Empty: Story = {
	args: {
		user: mockUser,
		achievements: [],
	},
};

export const FocusedSubset: Story = {
	args: {
		user: mockUser,
		achievements: mythicAchievements.filter((a) =>
			["commits", "pull_requests"].includes(a.category),
		),
	},
};

// Sanity check story: renders a small overlay and a single, known achievement to ensure
// the React Flow canvas and nodes are visible in Storybook. This helps debug cases when
// the tree appears blank due to layout or data mismatch.
export const SanityCheck: Story = {
	render: () => (
		<ReactFlowProvider>
			<div style={{ position: "relative", height: "80vh", background: "var(--background)" }}>
				<div style={{ position: "absolute", zIndex: 200, left: 12, top: 12, padding: 8 }}>
					<strong>SkillTree Sanity Check</strong>
				</div>
				<div style={{ height: "100%", paddingTop: 120 }}>
					<SkillTree
						user={mockUser}
						achievements={[
							{
								id: "first_pull",
								name: "First Pull",
								description: "Open your first pull request",
								category: "pull_requests",
								rarity: "common",
								parentId: undefined,
								status: "unlocked",
								icon: GitPullRequest,
								progress: 1,
								maxProgress: 1,
								unlockedAt: new Date(),
								progressData: { type: "BinaryAchievementProgress", unlocked: true },
							} as unknown as UIAchievement,
						]}
					/>
				</div>
			</div>
		</ReactFlowProvider>
	),
};

export const DataDebug: Story = {
	render: () => (
		<div style={{ padding: 12 }}>
			<h4>generateSkillTreeData (mock)</h4>
			<pre style={{ maxHeight: 420, overflow: "auto" }}>
				{JSON.stringify(generateSkillTreeData(mockUser, mythicAchievements), null, 2)}
			</pre>
		</div>
	),
};

export const MinimalFlow: Story = {
	render: () => (
		<ReactFlowProvider>
			<div style={{ height: 420 }}>
				<ReactFlow
					nodes={[{ id: "a", position: { x: 200, y: 200 }, data: { label: "A" } }]}
					edges={[]}
				/>
			</div>
		</ReactFlowProvider>
	),
};
