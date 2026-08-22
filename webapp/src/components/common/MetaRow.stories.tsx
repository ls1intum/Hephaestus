import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";
import { Badge } from "@/components/ui/badge";
import { MetaRow } from "./MetaRow";

/**
 * Captions and chips are two kinds of thing. Rendered as one flat run at one gap they arrive as a
 * single run-on sentence — "Pull or merge request ⏺Send automatically Follows the workspace
 * default" — which is what this row used to say.
 */
const meta = {
	title: "Common/Meta row",
	component: MetaRow,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		captions: ["Pull or merge request", "Follows the workspace default"],
		badges: <Badge variant="secondary">Review before sending</Badge>,
	},
} satisfies Meta<typeof MetaRow>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Pull or merge request")).toBeVisible();
		// The separator is decoration: it must not reach a screen reader as a word.
		await expect(canvas.getByText("·")).toHaveAttribute("aria-hidden", "true");
	},
};

/** One caption needs no separator. */
export const SingleCaption: Story = {
	args: { captions: ["Issue"] },
	play: async ({ canvas }) => {
		await expect(canvas.queryByText("·")).not.toBeInTheDocument();
	},
};

export const BadgesOnly: Story = {
	args: { captions: [] },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Review before sending")).toBeVisible();
	},
};

export const Crowded: Story = {
	args: {
		badges: (
			<>
				<Badge variant="secondary">Review before sending</Badge>
				<Badge variant="warning">Cannot review this yet</Badge>
				<Badge variant="outline">Catalog changed, yours did not</Badge>
			</>
		),
	},
};

export const DarkMode: Story = { globals: { theme: "dark" } };
