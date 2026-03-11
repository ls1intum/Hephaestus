import type { Meta, StoryObj } from "@storybook/react";
import { ReactFlowProvider } from "@xyflow/react";
import { SkillTree } from "@/components/achievements/SkillTree";
import { mockUser, mythicAchievementsUI } from "@/components/achievements/storyMockData";
import { generateSkillTreeData } from "@/components/achievements/utils";

/**
 * The full interactive Skill Tree visualization for Hephaestus achievements.
 * Maps divine achievements to a radial/compass-style layout.
 */
const meta = {
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
					<div className="pt-40 h-[calc(100vh-160px)]">
						<Story />
					</div>
				</div>
			</ReactFlowProvider>
		),
	],
} as Meta<typeof SkillTree>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Standard tree view for the Forge Master.
 */
export const Default: Story = {
	args: {
		user: mockUser,
		achievements: mythicAchievementsUI,
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
		achievements: mythicAchievementsUI.filter((a) =>
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
		achievements: mythicAchievementsUI,
	},
};

/**
 * Helper story to visualize the raw data being generated for React Flow.
 */
export const GeneratedDataPreview: Story = {
	render: () => (
		<div className="p-8 font-mono bg-background text-muted-foreground min-h-screen overflow-auto">
			<h4 className="text-xl font-bold mb-4 text-foreground">
				Skill Tree Generation Data (Raw Output)
			</h4>
			<pre className="text-xs">
				{JSON.stringify(generateSkillTreeData(mockUser, mythicAchievementsUI), null, 2)}
			</pre>
		</div>
	),
};
