import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, screen } from "storybook/test";
import { ReviewPracticeLink } from "./ReviewPracticeLink";
import { workspacePractices } from "./story-mock-data";

const thinControllers = workspacePractices.find((p) => p.slug === "thin-controllers");
if (!thinControllers) throw new Error("The practice fixtures no longer cover thin-controllers");

/**
 * The review read models carry a practice's slug, name and area but not its prose, so the hover card
 * needs the practice record itself. The screen fetches the workspace's practice list once and hands
 * the matching record down; a caller that has no record still gets a working link, which is why
 * `practice` is optional.
 */
const meta = {
	title: "Workspace admin/Practice reviews/Building blocks/Practice link",
	component: ReviewPracticeLink,
	parameters: { layout: "centered", chromatic: { viewports: [1440] } },
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		practiceSlug: thinControllers.slug,
		practiceName: thinControllers.name,
		area: { slug: "code-quality", name: "Code quality" },
		practice: thinControllers,
	},
} satisfies Meta<typeof ReviewPracticeLink>;

export default meta;
type Story = StoryObj<typeof meta>;

export const WithTheHoverCard: Story = {
	play: async ({ canvas, userEvent }) => {
		await userEvent.hover(canvas.getByRole("link", { name: /Thin controllers/ }));
		await screen.findByText(thinControllers.whyItMatters ?? "");
	},
};

/**
 * Nothing the card shows is load-bearing — the name and the area are on the row, and the rest is a
 * field on the page the link goes to — so a caller without the practice record, like a reader on a
 * touchscreen who gets no card either way, loses nothing.
 */
export const WithoutThePracticeRecord: Story = {
	args: { practice: undefined },
	play: async ({ canvas, userEvent }) => {
		const link = canvas.getByRole("link", { name: /Thin controllers/ });
		await expect(link).toHaveAttribute("href", "/w/demo/admin/practices/thin-controllers");
		await userEvent.hover(link);
		await expect(screen.queryByText(thinControllers.whyItMatters ?? "")).not.toBeInTheDocument();
	},
};

/**
 * The colour is what an operator scans an area by, so the name is carried by the `title` and the
 * screen-reader text rather than taking visible space on a row that already names the practice, the
 * person, the work and the time.
 */
export const AreaIsAMarkNotAWord: Story = {
	play: async ({ canvas }) => {
		canvas.getByTitle("Code quality");
		// The words are in the accessible name, not on screen: a row already names the practice, the
		// person, the work and the time.
		await expect(canvas.getByRole("link")).toHaveAccessibleName("Code quality: Thin controllers");
	},
};

/** Unassigned is a real state and not a missing value, so the mark is simply absent. */
export const PracticeWithNoArea: Story = {
	args: { area: undefined },
	play: async ({ canvas }) => {
		await expect(canvas.queryByTitle("Code quality")).not.toBeInTheDocument();
		canvas.getByRole("link", { name: "Thin controllers" });
	},
};
