import type { Meta, StoryObj } from "@storybook/react";
import { ReactFlowProvider } from "@xyflow/react";
import { StatsPanel } from "@/components/achievements/StatsPanel.tsx";
import { SkillTree } from "./SkillTree";
import { mockUser, mythicAchievements } from "./storyMockData";
import { generateSkillTreeData } from "./utils";

/**
 * The full interactive Skill Tree visualization for Hephaestus achievements.
 * Maps divine achievements to a radial/compass-style layout.
 */
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
				<div className="h-screen w-full bg-background text-foreground">
					{/* Add top padding so node tooltips aren't clipped in docs. */}
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

/**
 * Standard tree view for the Forge Master.
 */
export const Default: Story = {
	args: {
		user: mockUser,
		achievements: mythicAchievements,
	},
};

/**
 * Empty visualization when no divine sparks have been gathered yet.
 */
export const EmptyForge: Story = {
	args: {
		user: mockUser,
		achievements: [],
	},
};

/**
 * Visualization showing only progress toward core forging categories.
 */
export const CoreCategoriesOnly: Story = {
	args: {
		user: mockUser,
		achievements: mythicAchievements.filter((a) =>
			["commits", "pull_requests"].includes(a.category),
		),
	},
};

/**
 * The full Skill Tree visualization showing divine achievement progression.
 */
export const FullSkillTree: Story = {
	args: {
		user: mockUser,
		achievements: mythicAchievements,
	},
};

/**
 * Standalone statistics display showing progression breakdown.
 */
export const StatsPanelDisplay: StoryObj<typeof StatsPanel> = {
	render: () => (
		<div className="bg-background p-8 max-w-sm border rounded-xl">
			<StatsPanel achievements={mythicAchievements} />
		</div>
	),
	parameters: {
		layout: "centered",
	},
};

/**
 * Helper story to visualize the raw data being generated for React Flow.
 */
export const GeneratedDataPreview: Story = {
	render: () => (
		<div className="p-8 font-mono bg-slate-900 text-slate-300 min-h-screen overflow-auto">
			<h4 className="text-xl font-bold mb-4 text-white">Skill Tree Generation Data (Raw Output)</h4>
			<pre className="text-xs">
				{JSON.stringify(generateSkillTreeData(mockUser, mythicAchievements), null, 2)}
			</pre>
		</div>
	),
};
