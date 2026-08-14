import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { PageLayout } from "@/components/core/PageLayout";
import { withStandardPage } from "@/stories/decorators";
import { PracticeReviewsHeader } from "./PracticeReviewsLayout";

/**
 * The three review views, and which one you are in.
 *
 * <p>Worth knowing when changing this: `aria-current` is what draws the selected tab, and TanStack's
 * `Link` also sets it on any link it considers active — `...isActive && { "aria-current": "page" }`
 * is spread *after* the caller's props in `link.js`, so an explicit `aria-current={undefined}` here
 * cannot turn it off. Two mechanisms therefore decide the same attribute, and every story below
 * asserts the count rather than the identity of the current tab, so a second highlighted tab fails
 * the suite instead of being noticed in a screenshot.
 */
const meta = {
	title: "Workspace admin/Practice reviews/Navigation",
	component: PracticeReviewsHeader,
	parameters: {
		layout: "fullscreen",
		chromatic: { viewports: [320, 1440] },
		viewport: { defaultViewport: "reflow" },
	},
	args: {
		workspaceSlug: "demo",
		activeSection: "reviews",
	},
	decorators: [
		(Story) => (
			<PageLayout>
				<Story />
			</PageLayout>
		),
		withStandardPage,
	],
	tags: ["autodocs"],
} satisfies Meta<typeof PracticeReviewsHeader>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Exactly one tab is current, and it is the one named. */
async function expectOnlyCurrent(canvasElement: HTMLElement, name: string) {
	const nav = within(canvasElement).getByRole("navigation", {
		name: "Practice review sections",
	});
	expect(within(nav).getAllByRole("link", { current: "page" })).toHaveLength(1);
	within(nav).getByRole("link", { name, current: "page" });
}

export const Reviews: Story = {
	play: async ({ canvasElement }) => {
		await expectOnlyCurrent(canvasElement, "Reviews");
	},
};

export const Observations: Story = {
	args: { activeSection: "observations" },
	play: async ({ canvasElement }) => {
		await expectOnlyCurrent(canvasElement, "Observations");
	},
};

export const Delivery: Story = {
	args: { activeSection: "delivery" },
	play: async ({ canvasElement }) => {
		await expectOnlyCurrent(canvasElement, "Delivery");
	},
};

/**
 * Reviewed work is reached from any of the three, so it claims none of them.
 *
 * Marking one would tell the reader they had navigated somewhere they had not, and the back link
 * they want is the breadcrumb rather than a tab.
 */
export const ReviewedWork: Story = {
	args: { activeSection: undefined },
	play: async ({ canvasElement }) => {
		const nav = within(canvasElement).getByRole("navigation", {
			name: "Practice review sections",
		});
		expect(within(nav).queryAllByRole("link", { current: "page" })).toHaveLength(0);
	},
};
