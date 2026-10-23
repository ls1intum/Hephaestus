import type { Meta, StoryObj } from "@storybook/react-vite";

import { expectSectionLines } from "@/stories/section-lines";

import { Section } from "./Section";
import { SectionStack } from "./SectionStack";

function NothingToSay() {
	return null;
}

const meta = {
	component: SectionStack,
	parameters: { layout: "padded" },
	render: (args) => (
		<SectionStack {...args}>
			<Section level={3} size="lg" title="What it is">
				<div className="rounded-lg border p-4 text-sm">A card keeps its own frame.</div>
			</Section>
			{/* A section with nothing to show renders nothing, and leaves no line behind. */}
			<NothingToSay />
			<Section level={3} size="lg" title="State and history">
				<p className="text-sm">Section body.</p>
			</Section>
			<Section level={3} size="lg" title="Technical">
				<p className="text-sm">Section body.</p>
			</Section>
		</SectionStack>
	),
	tags: ["autodocs"],
} satisfies Meta<typeof SectionStack>;

export default meta;
type Story = StoryObj<typeof meta>;

/** One thin line between each two children, none above the first, the same room on each side. */
export const Default: Story = {
	play: async ({ canvasElement }) => {
		await expectSectionLines(canvasElement, 2);
	},
};

export const Dark: Story = {
	globals: { theme: "dark" },
	play: async ({ canvasElement }) => {
		await expectSectionLines(canvasElement, 2);
	},
};
