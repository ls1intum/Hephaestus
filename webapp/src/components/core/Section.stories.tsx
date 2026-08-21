import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";
import { Button } from "@/components/ui/button";
import { Section } from "./Section";

/**
 * The repo had five spellings of this one shape — `text-lg font-semibold` and `font-semibold text-lg`
 * counted as separate clusters — and about a third of them shipped without `aria-labelledby`. The
 * component cannot get that wrong: it generates the id and wires it up itself.
 */
const meta = {
	component: Section,
	parameters: { layout: "padded" },
	args: {
		title: "Review guidance",
		description: "What the reviewer looks for, and what the author should have written.",
		children: <p className="text-sm">Section body.</p>,
	},
	tags: ["autodocs"],
} satisfies Meta<typeof Section>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvas }) => {
		const region = canvas.getByRole("region", { name: "Review guidance" });
		// The region is named by its own heading, so a screen reader announces it on entry.
		await expect(region.getAttribute("aria-labelledby")).toBe(
			canvas.getByRole("heading", { name: "Review guidance" }).id,
		);
	},
};

export const Sizes: Story = {
	render: (args) => (
		<div className="space-y-8">
			<Section {...args} size="md" title="Medium — a section of an admin page" />
			<Section {...args} size="sm" title="Small — a subsection inside a panel" />
		</div>
	),
};

export const WithActions: Story = {
	args: { actions: <Button size="sm">Add practice</Button> },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("button", { name: "Add practice" })).toBeVisible();
	},
};

export const NestedLevel: Story = {
	args: { level: 3, size: "sm", title: "A subsection" },
	play: async ({ canvas }) => {
		// A section inside a section is an h3, so the document outline stays truthful.
		await expect(canvas.getByRole("heading", { level: 3, name: "A subsection" })).toBeVisible();
	},
};

export const TitleOnly: Story = {
	args: { description: undefined },
};

export const LongContent: Story = {
	args: {
		title:
			"Decisions, documentation, and long-lived operational knowledge that outlives its authors",
		description:
			"Practices covering how a team records the reasoning behind a change, keeps operational runbooks current, and makes the resulting knowledge findable long after the original authors have moved on.",
		actions: <Button size="sm">Review 12 practices</Button>,
	},
};

export const NarrowViewport: Story = {
	args: { actions: <Button size="sm">Add practice</Button> },
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
};

export const DarkMode: Story = {
	globals: { theme: "dark" },
};
