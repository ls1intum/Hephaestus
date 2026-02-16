import type { Meta, StoryObj } from "@storybook/react";
import { ReactFlowProvider } from "@xyflow/react";
import { AvatarNode } from "./AvatarNode";
import { CategoryLabels } from "./CategoryLabels";

/**
 * Stories for the CategoryLabels component.
 *
 * The component positions category labels around a skill-tree canvas. Stories
 * render the labels inside a fixed-size, relatively positioned container so the
 * absolute positioning in the component works as expected. Visuals use a
 * subtle radial background to suggest a skill tree — names and theme in the
 * docs borrow from a modern digital-mythology (Zeus, Athena, Hermes) to keep
 * the examples memorable when paired with other achievement stories.
 */
const meta = {
	component: CategoryLabels,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component: "Positions achievement category labels around the skill-tree canvas.",
			},
			source: { state: "closed" },
		},
	},
	tags: ["autodocs"],
	// Add padding so Storybook's docs chrome doesn't clip the absolute labels.
	decorators: [
		(Story) => (
			<div style={{ padding: 48 }}>
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof CategoryLabels>;

export default meta;
type Story = StoryObj<typeof meta>;

// Small presentational canvas used by stories — keeps styles colocated and
// explicit so the story remains easy to reason about.
function SkillTreeCanvas({ size = 600 }: { size?: number }) {
	return (
		<div
			style={{
				width: size,
				height: size,
				position: "relative",
				borderRadius: 16,
				// subtle radial background to suggest a skill-tree / node field
				background:
					"radial-gradient(circle at 50% 35%, rgba(99,102,241,0.06), transparent 40%), linear-gradient(180deg, rgba(15,23,42,0.02), rgba(15,23,42,0.04))",
				display: "flex",
				alignItems: "center",
				justifyContent: "center",
				boxShadow: "0 6px 18px rgba(2,6,23,0.06)",
			}}
		>
			{/* Placeholder central node to give visual context for the labels */}
			<div
				style={{
					width: "28%",
					height: "28%",
					borderRadius: "50%",
					background: "linear-gradient(180deg,#fff,#f3f4f6)",
					boxShadow: "inset 0 2px 8px rgba(2,6,23,0.04)",
					border: "1px solid rgba(2,6,23,0.04)",
				}}
			/>
			<CategoryLabels />

			{/* Avatar node centered in the skill tree for contextual rendering */}
			<ReactFlowProvider>
				<div
					style={{
						position: "absolute",
						left: "50%",
						top: "50%",
						transform: "translate(-50%, -50%)",
						zIndex: 20,
					}}
				>
					<AvatarNode
						{...({
							id: "root-avatar",
							type: "avatar",
							data: {
								level: 42,
								leaguePoints: 1600,
								avatarUrl: "https://github.com/github.png",
								name: "Zeus",
							},
							dragging: false,
							zIndex: 10,
							isConnectable: false,
							deletable: false,
							selectable: false,
							selected: false,
							draggable: false,
						} as any)}
					/>
				</div>
			</ReactFlowProvider>
		</div>
	);
}

/** Default rendering inside a centered skill-tree canvas. */
export const Default: Story = {
	render: () => <SkillTreeCanvas size={640} />,
};

/** Larger canvas to show label distribution at scale. */
export const LargeCanvas: Story = {
	render: () => <SkillTreeCanvas size={920} />,
};

/** Smaller canvas to exercise clipping / responsiveness. */
export const SmallCanvas: Story = {
	render: () => <SkillTreeCanvas size={420} />,
};

// /** Medium-sized canvas rendered in dark mode to verify theme colors. */
// export const DarkMode: Story = {
// 	render: () => (
// 		<div className="dark bg-background p-12">
// 			<SkillTreeCanvas size={640} />
// 		</div>
// 	),
// };
